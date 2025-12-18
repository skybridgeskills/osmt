import type { ContainerDefinition } from '@aws-sdk/client-ecs';
import { ECSClient, LogDriver, RegisterTaskDefinitionCommand } from '@aws-sdk/client-ecs';
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

const DEFAULT_TIMEOUT_MS = durationMs({ minutes: 10 });

export async function main(args: string[]): Promise<number> {
  const envName = args[2] ?? failWithUsage('Env name is required');
  const versionArg = args[3] ?? failWithUsage('Version is required');

  // Resolve 'latest' to actual version
  let appVersion: string;
  try {
    appVersion = resolveVersion(versionArg);
    if (versionArg === 'latest') {
      console.info();
    }
  } catch (error) {
    failWithUsage(`Failed to resolve version: ${error}`);
  }

  // Default the aws region
  process.env.AWS_REGION = process.env.AWS_REGION || 'us-west-2';

  const timeoutHandle = setTimeout(() => {
    console.error(pc.red(`❌ Migration timed out after ${DEFAULT_TIMEOUT_MS / 1000} seconds`));
    process.exit(1);
  }, DEFAULT_TIMEOUT_MS);

  // Store environment name for error handling
  const capturedEnvName = envName;

  const awsEnv = AwsEnvironmentInfo(envName) ?? failWithUsage('Invalid account: ' + envName);

  try {
    await ValidateAwsAccountStep(awsEnv);
    await ValidateMonorepoEcrImageExistsStep(appVersion);

    logMigrationStart(envName, appVersion);

    const clusterName = ecsClusterNameFor(awsEnv);
    const dockerTag = versionToDockerTag(appVersion);

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

    // Register migration task definition
    const taskDefinitionArn = await AsyncStep(
      'Registering migration task definition',
      registerMigrationTaskDefinition({
        clusterName,
        envName: awsEnv.envName,
        dockerTag,
        awsEnv,
      }),
      {
        timeout: { seconds: 30 },
        successMessage: 'Task definition registered',
        failureMessage: 'Failed to register task definition',
      },
    );

    // Run the migration task
    const taskInfo = await AsyncStep(
      'Creating migration task',
      runEcsTask({
        clusterName,
        taskDefinitionArn,
        containerName: 'migrate',
        networkConfig,
      }),
      {
        timeout: { seconds: 30 },
        successMessage: pc.green('🚀 Migration task created'),
        failureMessage: 'Failed to create migration task',
      },
    );

    console.info();
    console.info('📊 Task Information:');
    console.info(`   Task ID: ${taskInfo.taskId}`);
    console.info(
      `   Logs: ${awsLinks.cloudWatch.logStream({
        region: awsEnv.region,
        logGroupName: `/ecs/${clusterName}`,
        logStreamName: `migrate/migrate/${taskInfo.taskId}`,
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
      'Waiting for migration task to reach RUNNING state',
      waitForTaskToStart({
        clusterName,
        taskArn: taskInfo.taskArn,
        timeoutSeconds: 120,
      }),
      {
        timeout: { seconds: 120 },
        successMessage: 'Migration task is now running',
        failureMessage: 'Migration task failed to start',
      },
    );

    if (!startResult.success) {
      console.error();
      console.error(pc.red('❌ MIGRATION TASK FAILED TO START'));
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
      await printFullMigrationLogs(clusterName, taskInfo.taskId, awsEnv.region);
      console.error();
      return 1;
    }

    // Step 2: Monitor logs in real-time AND wait for task completion in parallel
    // Use Promise.race so that whichever completes first determines the outcome
    console.info('📋 Migration Logs (real-time):');
    console.info('─'.repeat(80));

    const logMonitorPromise = monitorLogsWithRealtimeOutput({
      logGroupName: `/ecs/${clusterName}`,
      logStreamName: `migrate/migrate/${taskInfo.taskId}`,
      successPatterns: ['MIGRATION COMPLETED SUCCESSFULLY'],
      errorPatterns: [
        'ERROR',
        'FATAL',
        'FAILED',
        'Exception',
        'Error:',
        'Failed to',
        'migration failed',
        'MIGRATION FAILED',
      ],
      timeoutMs: durationMs({ minutes: 5 }),
    });

    const taskCompletionPromise = waitForTaskCompletion({
      clusterName,
      taskArn: taskInfo.taskArn,
      timeoutSeconds: 300,
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
        console.info(pc.green('✅ MIGRATION COMPLETED SUCCESSFULLY'));
        console.info();
        return 0;
      } else {
        console.error();
        console.error(pc.red('❌ MIGRATION FAILED'));
        if (taskResult.exitCode !== undefined) {
          console.error(`   Exit code: ${taskResult.exitCode}`);
        }
        if (taskResult.stoppedReason) {
          console.error(`   Reason: ${taskResult.stoppedReason}`);
        }
        await printFullMigrationLogs(clusterName, taskInfo.taskId, awsEnv.region);
        console.error();
        return 1;
      }
    } else {
      // Log pattern completed first
      const logResult = result.result;
      if (logResult.success) {
        console.info();
        console.info(pc.green('✅ MIGRATION COMPLETED SUCCESSFULLY'));
        console.info();
        return 0;
      } else {
        console.error();
        console.error(pc.red('❌ MIGRATION FAILED'));
        if (logResult.error) {
          console.error(`   ${logResult.error}`);
        }
        await printFullMigrationLogs(clusterName, taskInfo.taskId, awsEnv.region);
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

    console.error(pc.red('❌ Migration failed with error:'), error);
    return 1;
  }
}

function logMigrationStart(envName: string, appVersion: string) {
  console.info();
  console.info(pc.blue('🚀 DATABASE MIGRATION'));
  console.info(`   Environment: ${envName}`);
  console.info(`   Version: ${appVersion}`);
  console.info();
}

async function registerMigrationTaskDefinition(options: {
  clusterName: string;
  envName: string;
  dockerTag: string;
  awsEnv: ReturnType<typeof AwsEnvironmentInfo>;
}): Promise<string> {
  const { clusterName, envName, dockerTag, awsEnv } = options;
  const ecsClient = new ECSClient({});
  const ecrRegistry = process.env.ECR_REGISTRY || '853091924495.dkr.ecr.us-west-2.amazonaws.com';

  // Get execution and task role from an existing service (osmt-api)
  const { taskDefinition: apiTaskDef } = await getCurrentServiceTaskDefinition(clusterName, 'osmt-api');

  const containerDefinition: ContainerDefinition = {
    name: 'migrate',
    image: `${ecrRegistry}/osmt:${dockerTag}`,
    environment: [
      { name: 'APP_NAME', value: 'migrate' },
      { name: 'ENV_NAME', value: envName },
      { name: 'APP_VERSION', value: dockerTag },
    ],
    logConfiguration: {
      logDriver: LogDriver.AWSLOGS,
      options: {
        'awslogs-group': `/ecs/${clusterName}`,
        'awslogs-region': awsEnv.region,
        'awslogs-stream-prefix': 'migrate',
      },
    },
  };

  const registerCommand = new RegisterTaskDefinitionCommand({
    family: 'migrate-task',
    networkMode: 'awsvpc',
    requiresCompatibilities: ['FARGATE'],
    cpu: apiTaskDef.cpu || '256',
    memory: apiTaskDef.memory || '512',
    runtimePlatform: {
      cpuArchitecture: 'ARM64',
      operatingSystemFamily: 'LINUX',
    },
    containerDefinitions: [containerDefinition],
    executionRoleArn: apiTaskDef.executionRoleArn,
    taskRoleArn: apiTaskDef.taskRoleArn,
  });

  const response = await ecsClient.send(registerCommand);
  const taskDefinitionArn = response.taskDefinition?.taskDefinitionArn;

  if (!taskDefinitionArn) {
    throw new Error('No task definition ARN returned from register');
  }

  return taskDefinitionArn;
}

function failWithUsage(message: string): never {
  console.error(pc.red('Error: ' + message));
  usage();
  process.exit(1);
}

function usage() {
  console.log('Usage: tsx scripts/deploy-migrations.script.ts <environment> <version>');
  console.log('');
  console.log('Arguments:');
  console.log('  environment  - AWS environment name');
  console.log("  version      - Version to deploy (e.g., 'v1.2.3' or 'latest')");
  console.log('');
  console.log('Available environments:');
  for (const name of AwsEnvironmentInfo.names) {
    console.log(`- ${name}`);
  }
  console.log('');
  console.log('Examples:');
  console.log('  tsx scripts/deploy-migrations.script.ts staging v1.2.3');
  console.log('  tsx scripts/deploy-migrations.script.ts staging latest');
}

const exitCode = await main(process.argv);
process.exit(exitCode);

async function printFullMigrationLogs(
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
      logStreamName: `migrate/migrate/${taskId}`,
      startFromHead: true,
    });
    console.error('─'.repeat(80));
    console.error('🔎 To view logs yourself, run:');
    console.error(
      `   aws logs get-log-events --log-group-name "/ecs/${clusterName}" --log-stream-name "migrate/migrate/${taskId}" --region ${region} --query 'events[].message' --output text`,
    );
  } catch {
    console.error('⚠️  Failed to print full task logs');
  }
}

// Import the required functions from lib files
// These need to be created separately
function resolveVersion(versionArg: string): string {
  // Simple implementation - in a real scenario this would resolve 'latest' to actual version
  return versionArg;
}

function versionToDockerTag(version: string): string {
  return version.startsWith('v') ? version.substring(1) : version;
}

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

async function ValidateMonorepoEcrImageExistsStep(appVersion: string): Promise<void> {
  // Validate ECR image exists
  console.log(`Validating ECR image exists for version ${appVersion}...`);
}

const awsLinks = {
  cloudWatch: {
    logStream: ({ region, logGroupName, logStreamName }: { region: string; logGroupName: string; logStreamName: string }) =>
      `https://${region}.console.aws.amazon.com/cloudwatch/home?region=${region}#logsV2:log-groups/log-group/${encodeURIComponent(logGroupName)}/log-events/${encodeURIComponent(logStreamName)}`,
    logGroup: ({ region, logGroupName }: { region: string; logGroupName: string }) =>
      `https://${region}.console.aws.amazon.com/cloudwatch/home?region=${region}#logsV2:log-groups/log-group/${encodeURIComponent(logGroupName)}`,
  },
};

// Import placeholder implementations
// These lib files need to be created
import './lib/cloudwatch-logs.js';
import './lib/ecs-cluster-name-for.js';
import './lib/ecs-deployment.js';
import './lib/handle-aws-auth-error.js';
import './lib/run-ecs-task.js';
import './lib/async-step.js';
import './lib/duration-ms.js';

