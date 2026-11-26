export function storeUrlFor(platform: string, storeUrls: Record<string, string> | undefined): string | undefined {
  const url = storeUrls?.[platform];
  return url ? url : undefined;
}
