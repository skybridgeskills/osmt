export function resolveVersion(versionArg: string): string {
  // Simple implementation - in a real scenario this would resolve 'latest' to actual version
  // from GitHub releases or some other source
  if (versionArg === 'latest') {
    // For now, just return a placeholder
    return 'latest';
  }
  return versionArg;
}

export function versionToDockerTag(version: string): string {
  return version.startsWith('v') ? version.substring(1) : version;
}

