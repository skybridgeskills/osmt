import {
  CloudWatchLogsClient,
  GetLogEventsCommand,
  DescribeLogStreamsCommand,
} from '@aws-sdk/client-cloudwatch-logs';

interface MonitorLogsOptions {
  logGroupName: string;
  logStreamName: string;
  successPatterns: string[];
  errorPatterns: string[];
  timeoutMs: number;
}

interface MonitorLogsResult {
  success: boolean;
  error?: string;
}

export async function monitorLogsWithRealtimeOutput(
  options: MonitorLogsOptions,
): Promise<MonitorLogsResult> {
  const { logGroupName, logStreamName, successPatterns, errorPatterns, timeoutMs } = options;

  return new Promise((resolve) => {
    const timeout = setTimeout(() => {
      resolve({ success: false, error: 'Log monitoring timed out' });
    }, timeoutMs);

    // Simple polling implementation
    const checkLogs = async () => {
      try {
        const client = new CloudWatchLogsClient({});
        const command = new GetLogEventsCommand({
          logGroupName,
          logStreamName,
          startFromHead: false,
        });

        const response = await client.send(command);
        const events = response.events || [];

        for (const event of events) {
          const message = event.message || '';
          console.log(message);

          // Check for success patterns
          for (const pattern of successPatterns) {
            if (message.includes(pattern)) {
              clearTimeout(timeout);
              resolve({ success: true });
              return;
            }
          }

          // Check for error patterns
          for (const pattern of errorPatterns) {
            if (message.includes(pattern)) {
              clearTimeout(timeout);
              resolve({ success: false, error: `Found error pattern: ${pattern}` });
              return;
            }
          }
        }

        // Continue polling
        setTimeout(checkLogs, 2000);
      } catch (error) {
        // Continue polling on errors
        setTimeout(checkLogs, 2000);
      }
    };

    checkLogs();
  });
}

export async function printLogStream(options: {
  logGroupName: string;
  logStreamName: string;
  startFromHead?: boolean;
}): Promise<void> {
  const { logGroupName, logStreamName, startFromHead = false } = options;

  try {
    const client = new CloudWatchLogsClient({});
    const command = new GetLogEventsCommand({
      logGroupName,
      logStreamName,
      startFromHead,
    });

    const response = await client.send(command);
    const events = response.events || [];

    for (const event of events) {
      console.error(event.message);
    }
  } catch (error) {
    console.error('Failed to retrieve log events:', error);
  }
}

