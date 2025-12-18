import { ECSClient, DescribeServicesCommand } from '@aws-sdk/client-ecs';

interface WaitForEcsServiceOptions {
  clusterName: string;
  serviceName: string;
  taskDefinitionArn: string;
  timeoutMs: number;
  pollIntervalMs: number;
}

interface WaitForEcsServiceResult {
  success: boolean;
  error?: string;
}

export async function WaitForEcsServiceStep(
  options: WaitForEcsServiceOptions,
): Promise<WaitForEcsServiceResult> {
  const { clusterName, serviceName, taskDefinitionArn, timeoutMs, pollIntervalMs } = options;

  const client = new ECSClient({});
  const startTime = Date.now();

  while (Date.now() - startTime < timeoutMs) {
    try {
      const command = new DescribeServicesCommand({
        cluster: clusterName,
        services: [serviceName],
      });

      const response = await client.send(command);
      const service = response.services?.[0];

      if (!service) {
        return { success: false, error: 'Service not found' };
      }

      // Check if the service is using the expected task definition
      if (service.taskDefinition === taskDefinitionArn) {
        // Check if deployments are stable
        const deployments = service.deployments || [];
        const runningDeployments = deployments.filter(d => d.status === 'PRIMARY' || d.status === 'ACTIVE');

        if (runningDeployments.length === 1) {
          const deployment = runningDeployments[0];
          if (deployment.desiredCount === deployment.runningCount) {
            return { success: true };
          }
        }
      }

      // Wait before checking again
      await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
    } catch (error) {
      return { success: false, error: `Failed to check service status: ${error}` };
    }
  }

  return { success: false, error: 'Timeout waiting for service deployment' };
}

