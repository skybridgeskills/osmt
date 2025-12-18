import { pc } from 'picocolors';

export interface AuthErrorInfo {
  isAuthError: boolean;
  errorType?: string;
}

export function detectAwsAuthError(error: any): AuthErrorInfo {
  if (!error) return { isAuthError: false };

  const errorMessage = error.message || error.toString();

  if (
    errorMessage.includes('Unable to locate credentials') ||
    errorMessage.includes('InvalidAccessKeyId') ||
    errorMessage.includes('SignatureDoesNotMatch') ||
    errorMessage.includes('InvalidToken') ||
    errorMessage.includes('ExpiredToken') ||
    errorMessage.includes('AccessDenied')
  ) {
    return { isAuthError: true, errorType: 'credentials' };
  }

  return { isAuthError: false };
}

export function printAwsAuthErrorMessage(envName: string, accountId: string): void {
  console.error(pc.red('❌ AWS Authentication Error'));
  console.error();
  console.error('This appears to be an AWS authentication issue. Please ensure:');
  console.error();
  console.error('1. You have valid AWS credentials configured');
  console.error('2. Your AWS profile has access to account:', pc.yellow(accountId));
  console.error('3. You have the correct IAM permissions for environment:', pc.yellow(envName));
  console.error();
  console.error('Try running:');
  console.error(`   aws sts get-caller-identity --profile ${envName}`);
  console.error();
  console.error('Or check your AWS credentials:');
  console.error('   aws configure list');
}

