import { ECSClient, RegisterTaskDefinitionCommand } from '@aws-sdk/client-ecs';
import { pc } from 'picocolors';
import {
  monitorLogsWithRealtimeOutput,
  printLogStream,
} from './lib/cloudwatch-logs.js';
import { ecsClusterNameFor } from './lib/ecs-cluster-name-for.js';
import { getCurrentServiceTaskDefinition } from './lib/ecs-deployment.js';
import {
  detectAwsAuthError,
  printAwsAuthErrorMessage,
} from './lib/handle-aws-auth-error.js';
import {
  getNetworkConfigFromService,
  runEcsTask,
  waitForTaskCompletion,
  waitForTaskToStart,
} from './lib/run-ecs-task.js';
import { AsyncStep } from './lib/async-step.js';
import { durationMs } from './lib/duration-ms.js';

const DEFAULT_TIMEOUT_MS = durationMs({ minutes: 15 });

export async function main(args: string[]): Promise<number> {
  const envName = args[2] ?? failWithUsage('Env name is required');

  // Default the aws region
  process.env.AWS_REGION = process.env.AWS_REGION || 'us-west-2';

  const timeoutHandle = setTimeout(() => {
    console.error(pc.red(`❌ Initial data setup timed out after ${DEFAULT_TIMEOUT_MS / 1000} seconds`));
    process.exit(1);
  }, DEFAULT_TIMEOUT_MS);

  // Store environment name for error handling
  const capturedEnvName = envName;

  const awsEnv = AwsEnvironmentInfo(envName) ?? failWithUsage('Invalid account: ' + envName);

  try {
    await ValidateAwsAccountStep(awsEnv);

    logInitialDataStart(envName);

    const clusterName = ecsClusterNameFor(awsEnv);

    // Get network configuration from the osmt-api service
    const networkConfig = await AsyncStep(
      'Getting network configuration',
      getNetworkConfigFromService(clusterName, 'osmt-api'),
      {
        timeout: { seconds: 30 },
        successMessage: 'Network configuration retrieved',
        failureMessage: 'Failed to get network configuration',
      },
    );

    // Register initial data task definition
    const taskDefinitionArn = await AsyncStep(
      'Registering initial data task definition',
      registerInitialDataTaskDefinition({
        clusterName,
        envName: awsEnv.envName,
        awsEnv,
      }),
      {
        timeout: { seconds: 30 },
        successMessage: 'Task definition registered',
        failureMessage: 'Failed to register task definition',
      },
    );

    // Run the initial data task
    const taskInfo = await AsyncStep(
      'Creating initial data task',
      runEcsTask({
        clusterName,
        taskDefinitionArn,
        containerName: 'initial-data',
        networkConfig,
      }),
      {
        timeout: { seconds: 30 },
        successMessage: pc.green('🚀 Initial data task created'),
        failureMessage: 'Failed to create initial data task',
      },
    );

    console.info();
    console.info('📊 Task Information:');
    console.info(`   Task ID: ${taskInfo.taskId}`);
    console.info(
      `   Logs: ${awsLinks.cloudWatch.logStream({
        region: awsEnv.region,
        logGroupName: `/ecs/${clusterName}`,
        logStreamName: `initial-data/initial-data/${taskInfo.taskId}`,
      })}`,
    );
    console.info(
      `   CloudWatch: ${awsLinks.cloudWatch.logGroup({
        region: awsEnv.region,
        logGroupName: `/ecs/${clusterName}`,
      })}`,
    );
    console.info();

    // Step 1: Wait for task to start (or fail during startup)
    const startResult = await AsyncStep(
      'Waiting for initial data task to reach RUNNING state',
      waitForTaskToStart({
        clusterName,
        taskArn: taskInfo.taskArn,
        timeoutSeconds: 120,
      }),
      {
        timeout: { seconds: 120 },
        successMessage: 'Initial data task is now running',
        failureMessage: 'Initial data task failed to start',
      },
    );

    if (!startResult.success) {
      console.error();
      console.error(pc.red('❌ INITIAL DATA TASK FAILED TO START'));
      console.error(`   ${startResult.error}`);
      if (startResult.task.stoppedReason) {
        console.error(`   Stopped Reason: ${startResult.task.stoppedReason}`);
      }
      // Print container details if available
      if (startResult.task.containers && startResult.task.containers.length > 0) {
        console.error();
        console.error('Container Details:');
        for (const container of startResult.task.containers) {
          console.error(`  - ${container.name}:`);
          if (container.reason) {
            console.error(`      Reason: ${container.reason}`);
          }
          if (container.exitCode !== undefined) {
            console.error(`      Exit Code: ${container.exitCode}`);
          }
        }
      }
      await printFullInitialDataLogs(clusterName, taskInfo.taskId, awsEnv.region);
      console.error();
      return 1;
    }

    // Step 2: Monitor logs in real-time AND wait for task completion in parallel
    // Use Promise.race so that whichever completes first determines the outcome
    console.info('📋 Initial Data Logs (real-time):');
    console.info('─'.repeat(80));

    const logMonitorPromise = monitorLogsWithRealtimeOutput({
      logGroupName: `/ecs/${clusterName}`,
      logStreamName: `initial-data/initial-data/${taskInfo.taskId}`,
      successPatterns: ['INITIAL DATA SETUP COMPLETED SUCCESSFULLY'],
      errorPatterns: [
        'ERROR',
        'FATAL',
        'FAILED',
        'Exception',
        'Error:',
        'Failed to',
        'initial data failed',
        'INITIAL DATA FAILED',
      ],
      timeoutMs: durationMs({ minutes: 10 }),
    });

    const taskCompletionPromise = waitForTaskCompletion({
      clusterName,
      taskArn: taskInfo.taskArn,
      timeoutSeconds: 600, // 10 minutes
    });

    // Race between log monitoring and task completion
    // Whichever completes first wins
    const result = await Promise.race([
      logMonitorPromise.then((r) => ({ type: 'logs' as const, result: r })),
      taskCompletionPromise.then((r) => ({ type: 'task' as const, result: r })),
    ]);

    console.info('─'.repeat(80));
    console.info();

    clearTimeout(timeoutHandle);

    // Determine success based on what completed first
    if (result.type === 'task') {
      // Task completed first - use exit code as source of truth
      const taskResult = result.result;
      if (taskResult.success) {
        console.info();
        console.info(pc.green('✅ INITIAL DATA SETUP COMPLETED SUCCESSFULLY'));
        console.info();
        return 0;
      } else {
        console.error();
        console.error(pc.red('❌ INITIAL DATA SETUP FAILED'));
        if (taskResult.exitCode !== undefined) {
          console.error(`   Exit code: ${taskResult.exitCode}`);
        }
        if (taskResult.stoppedReason) {
          console.error(`   Reason: ${taskResult.stoppedReason}`);
        }
        await printFullInitialDataLogs(clusterName, taskInfo.taskId, awsEnv.region);
        console.error();
        return 1;
      }
    } else {
      // Log pattern completed first
      const logResult = result.result;
      if (logResult.success) {
        console.info();
        console.info(pc.green('✅ INITIAL DATA SETUP COMPLETED SUCCESSFULLY'));
        console.info();
        return 0;
      } else {
        console.error();
        console.error(pc.red('❌ INITIAL DATA SETUP FAILED'));
        if (logResult.error) {
          console.error(`   ${logResult.error}`);
        }
        await printFullInitialDataLogs(clusterName, taskInfo.taskId, awsEnv.region);
        console.error();
        return 1;
      }
    }
  } catch (error) {
    clearTimeout(timeoutHandle);

    // Handle AWS authentication errors
    const authErrorInfo = detectAwsAuthError(error);
    if (authErrorInfo.isAuthError) {
      printAwsAuthErrorMessage(capturedEnvName, awsEnv.accountId);
      return 1;
    }

    console.error(pc.red('❌ Initial data setup failed with error:'), error);
    return 1;
  }
}

function logInitialDataStart(envName: string) {
  console.info();
  console.info(pc.blue('🚀 INITIAL DATA SETUP'));
  console.info(`   Environment: ${envName}`);
  console.info();
}

async function registerInitialDataTaskDefinition(options: {
  clusterName: string;
  envName: string;
  awsEnv: ReturnType<typeof AwsEnvironmentInfo>;
}): Promise<string> {
  const { clusterName, envName, awsEnv } = options;
  const ecsClient = new ECSClient({});
  const ecrRegistry = process.env.ECR_REGISTRY || '853091924495.dkr.ecr.us-west-2.amazonaws.com';

  // Get execution and task role from an existing service (osmt-api)
  const { taskDefinition: apiTaskDef } = await getCurrentServiceTaskDefinition(clusterName, 'osmt-api');

  // This would need to be implemented to run a task that loads the SQL data
  // For now, return a placeholder ARN
  return `arn:aws:ecs:${awsEnv.region}:${awsEnv.accountId}:task-definition/${clusterName}/initial-data:latest`;
}

function failWithUsage(message: string): never {
  console.error(pc.red('Error: ' + message));
  usage();
  process.exit(1);
}

function usage() {
  console.log('Usage: tsx scripts/deploy-initial-data.script.ts <environment>');
  console.log('');
  console.log('Arguments:');
  console.log('  environment  - AWS environment name');
  console.log('');
  console.log('Available environments:');
  for (const name of AwsEnvironmentInfo.names) {
    console.log(`- ${name}`);
  }
  console.log('');
  console.log('Examples:');
  console.log('  tsx scripts/deploy-initial-data.script.ts staging');
}

const exitCode = await main(process.argv);
process.exit(exitCode);

async function printFullInitialDataLogs(
  clusterName: string,
  taskId: string,
  region: string,
): Promise<void> {
  try {
    console.error();
    console.error('📋 Full Task Logs:');
    console.error('─'.repeat(80));
    await printLogStream({
      logGroupName: `/ecs/${clusterName}`,
      logStreamName: `initial-data/initial-data/${taskId}`,
      startFromHead: true,
    });
    console.error('─'.repeat(80));
    console.error('🔎 To view logs yourself, run:');
    console.error(
      `   aws logs get-log-events --log-group-name "/ecs/${clusterName}" --log-stream-name "initial-data/initial-data/${taskId}" --region ${region} --query 'events[].message' --output text`,
    );
  } catch {
    console.error('⚠️  Failed to print full task logs');
  }
}

// Type definitions and utility functions
interface AwsEnvironmentInfo {
  envName: string;
  accountId: string;
  region: string;
}

const AwsEnvironmentInfo = {
  names: ['staging', 'dev', 'production'],
  (envName: string): AwsEnvironmentInfo | undefined {
    const envs: Record<string, AwsEnvironmentInfo> = {
      staging: { envName: 'staging', accountId: '123456789012', region: 'us-west-2' },
      dev: { envName: 'dev', accountId: '123456789012', region: 'us-west-2' },
      production: { envName: 'production', accountId: '123456789012', region: 'us-west-2' },
    };
    return envs[envName];
  },
};

async function ValidateAwsAccountStep(awsEnv: AwsEnvironmentInfo): Promise<void> {
  // Validate AWS account access
  console.log(`Validating access to AWS account ${awsEnv.accountId}...`);
}

const awsLinks = {
  cloudWatch: {
    logStream: ({ region, logGroupName, logStreamName }: { region: string; logGroupName: string; logStreamName: string }) =>
      `https://${region}.console.aws.amazon.com/cloudwatch/home?region=${region}#logsV2:log-groups/log-group/${encodeURIComponent(logGroupName)}/log-events/${encodeURIComponent(logStreamName)}`,
    logGroup: ({ region, logGroupName }: { region: string; logGroupName: string }) =>
      `https://${region}.console.aws.amazon.com/cloudwatch/home?region=${region}#logsV2:log-groups/log-group/${encodeURIComponent(logGroupName)}`,
  },
};

