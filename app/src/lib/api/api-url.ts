type ApiUrlInput = {
  url: string | undefined;
  isDevelopment: boolean;
  isWeb: boolean;
};

function isHttps(url: string): boolean {
  try {
    return new URL(url).protocol === 'https:';
  } catch {
    return false;
  }
}

export function resolveApiUrl({ url, isDevelopment, isWeb }: ApiUrlInput): string {
  if (isDevelopment) {
    return url ?? '';
  }
  if (isWeb && !url) {
    return '';
  }
  if (!url || !isHttps(url)) {
    throw new Error('EXPO_PUBLIC_API_URL must use https outside development');
  }
  return url;
}
