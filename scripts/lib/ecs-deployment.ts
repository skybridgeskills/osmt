import {
  ECSClient,
  DescribeServicesCommand,
  DescribeTaskDefinitionCommand,
} from '@aws-sdk/client-ecs';

export async function getCurrentServiceTaskDefinition(
  clusterName: string,
  serviceName: string,
): Promise<{ taskDefinition: any }> {
  const client = new ECSClient({});

  // Get the service to find the current task definition
  const describeServicesCommand = new DescribeServicesCommand({
    cluster: clusterName,
    services: [serviceName],
  });

  const servicesResponse = await client.send(describeServicesCommand);
  const service = servicesResponse.services?.[0];

  if (!service?.taskDefinition) {
    throw new Error(`Service ${serviceName} not found or has no task definition`);
  }

  // Get the task definition details
  const describeTaskDefCommand = new DescribeTaskDefinitionCommand({
    taskDefinition: service.taskDefinition,
  });

  const taskDefResponse = await client.send(describeTaskDefCommand);

  return {
    taskDefinition: taskDefResponse.taskDefinition,
  };
}

