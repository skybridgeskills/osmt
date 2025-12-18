interface AwsEnvironmentInfo {
  envName: string;
  accountId: string;
  region: string;
}

export function ecsClusterNameFor(awsEnv: AwsEnvironmentInfo): string {
  return `osmt-${awsEnv.envName}`;
}

