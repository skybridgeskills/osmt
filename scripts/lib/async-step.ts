import { pc } from 'picocolors';

export interface AsyncStepOptions {
  timeout?: { seconds: number };
  successMessage?: string;
  failureMessage?: string;
}

export async function AsyncStep<T>(
  description: string,
  operation: Promise<T>,
  options: AsyncStepOptions = {},
): Promise<T> {
  const { timeout, successMessage, failureMessage } = options;

  console.info(`${pc.blue('⏳')} ${description}...`);

  let timeoutHandle: NodeJS.Timeout | undefined;
  if (timeout) {
    timeoutHandle = setTimeout(() => {
      console.error(pc.red(`❌ ${description} timed out after ${timeout.seconds} seconds`));
      process.exit(1);
    }, timeout.seconds * 1000);
  }

  try {
    const result = await operation;
    if (timeoutHandle) {
      clearTimeout(timeoutHandle);
    }
    if (successMessage) {
      console.info(`${pc.green('✅')} ${successMessage}`);
    }
    return result;
  } catch (error) {
    if (timeoutHandle) {
      clearTimeout(timeoutHandle);
    }
    if (failureMessage) {
      console.error(`${pc.red('❌')} ${failureMessage}`);
    }
    throw error;
  }
}

