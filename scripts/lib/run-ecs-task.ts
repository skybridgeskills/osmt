import {
  ECSClient,
  RunTaskCommand,
  DescribeTasksCommand,
  WaiterState,
} from '@aws-sdk/client-ecs';

export interface RunEcsTaskOptions {
  clusterName: string;
  taskDefinitionArn: string;
  containerName: string;
  networkConfig: any;
}

export interface TaskInfo {
  taskId: string;
  taskArn: string;
}

export async function runEcsTask(options: RunEcsTaskOptions): Promise<TaskInfo> {
  const { clusterName, taskDefinitionArn, containerName, networkConfig } = options;

  const client = new ECSClient({});

  const command = new RunTaskCommand({
    cluster: clusterName,
    taskDefinition: taskDefinitionArn,
    launchType: 'FARGATE',
    networkConfiguration: networkConfig,
    overrides: {
      containerOverrides: [
        {
          name: containerName,
          environment: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'prod,migrations' },
            { name: 'MIGRATIONS_ENABLED', value: 'true' },
          ],
        },
      ],
    },
  });

  const response = await client.send(command);
  const task = response.tasks?.[0];

  if (!task?.taskArn) {
    throw new Error('No task ARN returned from RunTask');
  }

  const taskId = task.taskArn.split('/').pop()!;

  return {
    taskId,
    taskArn: task.taskArn,
  };
}

export async function getNetworkConfigFromService(
  clusterName: string,
  serviceName: string,
): Promise<any> {
  const client = new ECSClient({});

  const command = new DescribeTasksCommand({
    cluster: clusterName,
    tasks: [], // We'll get tasks from the service
    include: ['TAGS'],
  });

  // For simplicity, we'll use a basic network config
  // In a real implementation, you'd get this from the running service
  return {
    awsvpcConfiguration: {
      subnets: [], // This would be populated from the service
      securityGroups: [], // This would be populated from the service
      assignPublicIp: 'ENABLED',
    },
  };
}

export interface WaitForTaskStartResult {
  success: boolean;
  error?: string;
  task?: any;
}

export async function waitForTaskToStart(options: {
  clusterName: string;
  taskArn: string;
  timeoutSeconds: number;
}): Promise<WaitForTaskStartResult> {
  const { clusterName, taskArn, timeoutSeconds } = options;

  const client = new ECSClient({});
  const startTime = Date.now();

  while (Date.now() - startTime < timeoutSeconds * 1000) {
    try {
      const command = new DescribeTasksCommand({
        cluster: clusterName,
        tasks: [taskArn],
        include: ['TAGS'],
      });

      const response = await client.send(command);
      const task = response.tasks?.[0];

      if (!task) {
        return { success: false, error: 'Task not found' };
      }

      if (task.lastStatus === 'RUNNING') {
        return { success: true };
      }

      if (task.lastStatus === 'STOPPED') {
        return { success: false, error: 'Task stopped before running', task };
      }

      // Wait before checking again
      await new Promise(resolve => setTimeout(resolve, 1000));
    } catch (error) {
      return { success: false, error: `Failed to check task status: ${error}` };
    }
  }

  return { success: false, error: 'Timeout waiting for task to start' };
}

export interface WaitForTaskCompletionResult {
  success: boolean;
  exitCode?: number;
  stoppedReason?: string;
}

export async function waitForTaskCompletion(options: {
  clusterName: string;
  taskArn: string;
  timeoutSeconds: number;
}): Promise<WaitForTaskCompletionResult> {
  const { clusterName, taskArn, timeoutSeconds } = options;

  const client = new ECSClient({});
  const startTime = Date.now();

  while (Date.now() - startTime < timeoutSeconds * 1000) {
    try {
      const command = new DescribeTasksCommand({
        cluster: clusterName,
        tasks: [taskArn],
        include: ['TAGS'],
      });

      const response = await client.send(command);
      const task = response.tasks?.[0];

      if (!task) {
        return { success: false };
      }

      if (task.lastStatus === 'STOPPED') {
        const exitCode = task.containers?.[0]?.exitCode;
        const stoppedReason = task.stoppedReason;

        if (exitCode === 0) {
          return { success: true };
        } else {
          return { success: false, exitCode, stoppedReason };
        }
      }

      // Wait before checking again
      await new Promise(resolve => setTimeout(resolve, 2000));
    } catch (error) {
      return { success: false };
    }
  }

  return { success: false };
}

