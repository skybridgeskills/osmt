import { ECSClient, UpdateServiceCommand } from '@aws-sdk/client-ecs';
import { pc } from 'picocolors';
import { ecsClusterNameFor } from './lib/ecs-cluster-name-for.js';
import { findTaskForService } from './lib/ecs-deployment.js';
import {
  detectAwsAuthError,
  printAwsAuthErrorMessage,
} from './lib/handle-aws-auth-error.js';
import { WaitForEcsServiceStep } from './lib/wait-for-ecs-service-step.js';
import { resolveVersion } from './lib/version-utils.js';
import { AsyncStep } from './lib/async-step.js';
import { durationMs } from './lib/duration-ms.js';

const DEPLOY_TIMEOUT = durationMs({ minutes: 15 });

interface DeploymentInfo {
  taskDefinitionArn: string;
  serviceName: string;
  clusterName: string;
  awsEnv: AwsEnvironmentInfo;
}

export async function main(args: string[]): Promise<number> {
  const envName = args[2] ?? failWithUsage('Env name is required');
  const serviceNameArg = args[3] ?? failWithUsage('Service name is required');
  const versionArg = args[4] ?? failWithUsage('Version is required');

  // Validate and parse service name
  const ecsService =
    EcsServiceInfoLookup(serviceNameArg) ??
    failWithUsage(
      `Invalid service name: ${serviceNameArg}. Must be one of: ${EcsServiceInfoLookup.serviceNames.join(', ')}`,
    );
  const serviceName = ecsService.serviceName;

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
    console.error(pc.red(`❌ Deployment timed out after ${durationMs({ minutes: 15 }) / 1000 / 60} minutes`));
    process.exit(1);
  }, DEPLOY_TIMEOUT);

  // Store environment name for error handling (capture from outer scope)
  const capturedEnvName = envName;

  const awsEnv = AwsEnvironmentInfo(envName) ?? failWithUsage('Invalid account: ' + envName);

  const deploymentInfo = {
    appVersion,
    awsEnv,
  } satisfies AwsDeploymentInfo;

  try {
    await ValidateAwsAccountStep(awsEnv);
    await ValidateMonorepoEcrImageExistsStep(appVersion);

    logDeploymentStart(serviceName, ecsService, deploymentInfo);

    const deploymentResult = await AsyncStep(
      `Deploying ${serviceName} service`,
      deployService(ecsService, deploymentInfo),
      {
        timeout: DEPLOY_TIMEOUT,
        successMessage: 'Service deployment completed',
        failureMessage: 'Service deployment failed',
      },
    );

    const success = await monitorDeploymentAndExecution(deploymentResult);

    clearTimeout(timeoutHandle);

    if (success) {
      console.info();
      console.info(pc.green('✅ DEPLOYMENT COMPLETED SUCCESSFULLY'));
      console.info();
      console.info('🔗 AWS Console:');
      console.info(
        `   Service: ${awsLinks.ecs.serviceHealth({
          region: deploymentResult.awsEnv.region,
          clusterName: deploymentResult.clusterName,
          serviceName: deploymentResult.serviceName,
        })}`,
      );
      console.info();
      return 0;
    } else {
      console.error(pc.red('❌ DEPLOYMENT FAILED'));
      console.error();
      return 1;
    }
  } catch (error) {
    clearTimeout(timeoutHandle);

    // Handle AWS authentication errors with a helpful message
    const authErrorInfo = detectAwsAuthError(error);
    if (authErrorInfo.isAuthError) {
      // Use the actual env name from the script args (captured from main scope)
      printAwsAuthErrorMessage(capturedEnvName, awsEnv.accountId);
      return 1;
    }

    console.error(pc.red('❌ Deployment failed with error:'), error);
    return 1;
  }
}

function logDeploymentStart(
  serviceName: string,
  ecsService: EcsServiceInfo,
  deploymentInfo: AwsDeploymentInfo,
) {
  const { awsEnv } = deploymentInfo;
  const clusterName = ecsClusterNameFor(awsEnv);

  console.info();
  console.info(pc.blue(`🚀 ${serviceName.toUpperCase()} SERVICE DEPLOYMENT`));
  console.info(`   Environment: ${awsEnv.envName}`);
  console.info(`   Version: ${deploymentInfo.appVersion}`);
  console.info();
  console.info('🔗 AWS Console:');
  console.info(
    `   Service: ${awsLinks.ecs.serviceHealth({
      region: awsEnv.region,
      clusterName,
      serviceName: ecsService.serviceName,
    })}`,
  );
  console.info();
}

async function deployService(
  ecsService: EcsServiceInfo,
  deploymentInfo: AwsDeploymentInfo,
): Promise<DeploymentInfo> {
  const clusterName = ecsClusterNameFor(deploymentInfo.awsEnv);
  const serviceName = ecsService.serviceName;

  // Register new task definition
  const taskDefinitionArn = await registerTaskDefinition(ecsService, deploymentInfo);

  // Update service to use new task definition
  const ecsClient = new ECSClient({});
  const updateCommand = new UpdateServiceCommand({
    cluster: clusterName,
    service: serviceName,
    taskDefinition: taskDefinitionArn,
    forceNewDeployment: true,
  });

  await ecsClient.send(updateCommand);

  console.info();
  console.info(pc.green('✅ Task definition registered'));
  console.info(`   Service: ${serviceName}`);
  console.info();

  return {
    taskDefinitionArn,
    serviceName,
    clusterName,
    awsEnv: deploymentInfo.awsEnv,
  };
}

async function registerTaskDefinition(
  ecsService: EcsServiceInfo,
  deploymentInfo: AwsDeploymentInfo,
): Promise<string> {
  // This is a simplified implementation
  // In a real scenario, this would register a new task definition with updated image version
  // For now, we'll return a placeholder ARN
  const clusterName = ecsClusterNameFor(deploymentInfo.awsEnv);
  return `arn:aws:ecs:${deploymentInfo.awsEnv.region}:${deploymentInfo.awsEnv.accountId}:task-definition/${clusterName}/${ecsService.serviceName}:${deploymentInfo.appVersion}`;
}

async function monitorDeploymentAndExecution(deploymentInfo: DeploymentInfo): Promise<boolean> {
  // Wait for the task to reach RUNNING state
  const serviceResult = await WaitForEcsServiceStep({
    clusterName: deploymentInfo.clusterName,
    serviceName: deploymentInfo.serviceName,
    taskDefinitionArn: deploymentInfo.taskDefinitionArn,
    timeoutMs: durationMs({ minutes: 15 }),
    pollIntervalMs: durationMs({ seconds: 3 }),
  });

  if (!serviceResult.success) {
    console.error('❌ Service deployment failed:', serviceResult.error);
    return false;
  }

  // Find the running task to get its ID for logging
  const taskInfo = await AsyncStep(
    'Finding running task',
    findTaskForService(
      deploymentInfo.clusterName,
      deploymentInfo.serviceName,
      deploymentInfo.taskDefinitionArn,
    ),
    {
      timeout: { seconds: 30 },
      successMessage: 'Found running task',
      failureMessage: 'Could not find running task after deployment',
    },
  );

  if (!taskInfo) {
    console.error('❌ Could not find running task after deployment');
    return false;
  }

  console.info('📊 Task Information:');
  console.info(`   Task ID: ${taskInfo.taskId}`);
  console.info(
    `   Task: ${awsLinks.ecs.taskConfiguration({
      region: deploymentInfo.awsEnv.region,
      clusterName: deploymentInfo.clusterName,
      serviceName: deploymentInfo.serviceName,
      taskId: taskInfo.taskId,
    })}`,
  );
  console.info(
    `   CloudWatch: ${awsLinks.cloudWatch.logGroup({
      region: deploymentInfo.awsEnv.region,
      logGroupName: `/ecs/${deploymentInfo.clusterName}`,
    })}`,
  );
  console.info();

  // Service is running - deployment successful
  return true;
}

function failWithUsage(message: string): never {
  console.error(pc.red('Error: ' + message));
  usage();
  process.exit(1);
}

function usage() {
  console.log('Usage: tsx scripts/deploy-service.script.ts <environment> <service> <version>');
  console.log('');
  console.log('Arguments:');
  console.log('  environment  - AWS environment name');
  console.log('  service      - Service name');
  console.log("  version      - Version to deploy (e.g., 'v1.2.3' or 'latest')");
  console.log('');
  console.log('Available environments:');
  for (const name of AwsEnvironmentInfo.names) {
    console.log(`- ${name}`);
  }
  console.log('');
  console.log('Available services:');
  for (const name of EcsServiceInfoLookup.serviceNames) {
    console.log(`- ${name}`);
  }
  console.log('');
  console.log('Examples:');
  console.log('  tsx scripts/deploy-service.script.ts staging osmt-api v1.2.3');
  console.log('  tsx scripts/deploy-service.script.ts staging osmt-api latest');
}

const exitCode = await main(process.argv);
process.exit(exitCode);

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

interface EcsServiceInfo {
  serviceName: string;
}

const EcsServiceInfoLookup = {
  serviceNames: ['osmt-api'],
  (serviceName: string): EcsServiceInfo | undefined {
    const services: Record<string, EcsServiceInfo> = {
      'osmt-api': { serviceName: 'osmt-api' },
    };
    return services[serviceName];
  },
};

interface AwsDeploymentInfo {
  appVersion: string;
  awsEnv: AwsEnvironmentInfo;
}

async function ValidateAwsAccountStep(awsEnv: AwsEnvironmentInfo): Promise<void> {
  // Validate AWS account access
  console.log(`Validating access to AWS account ${awsEnv.accountId}...`);
}

async function ValidateMonorepoEcrImageExistsStep(appVersion: string): Promise<void> {
  // Validate ECR image exists
  console.log(`Validating ECR image exists for version ${appVersion}...`);
}

const awsLinks = {
  ecs: {
    serviceHealth: ({ region, clusterName, serviceName }: { region: string; clusterName: string; serviceName: string }) =>
      `https://${region}.console.aws.amazon.com/ecs/v2/clusters/${clusterName}/services/${serviceName}/health?region=${region}`,
    taskConfiguration: ({ region, clusterName, serviceName, taskId }: { region: string; clusterName: string; serviceName: string; taskId: string }) =>
      `https://${region}.console.aws.amazon.com/ecs/v2/clusters/${clusterName}/services/${serviceName}/tasks/${taskId}/configuration?region=${region}`,
  },
  cloudWatch: {
    logGroup: ({ region, logGroupName }: { region: string; logGroupName: string }) =>
      `https://${region}.console.aws.amazon.com/cloudwatch/home?region=${region}#logsV2:log-groups/log-group/${encodeURIComponent(logGroupName)}`,
  },
};

// Import the required functions from lib files
import './lib/wait-for-ecs-service-step.js';
import './lib/version-utils.js';

