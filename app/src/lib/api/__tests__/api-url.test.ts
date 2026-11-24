import { resolveApiUrl } from '../api-url';

describe('resolveApiUrl', () => {
  it('accepts an https url outside development', () => {
    expect(resolveApiUrl({ url: 'https://api.example.org', isDevelopment: false, isWeb: false })).toBe('https://api.example.org');
  });

  it('accepts a plain http url in development', () => {
    expect(resolveApiUrl({ url: 'http://localhost:8080', isDevelopment: true, isWeb: false })).toBe('http://localhost:8080');
  });

  it('accepts a missing url in development', () => {
    expect(resolveApiUrl({ url: undefined, isDevelopment: true, isWeb: false })).toBe('');
  });

  it('refuses a plain http url outside development', () => {
    expect(() => resolveApiUrl({ url: 'http://api.example.org', isDevelopment: false, isWeb: false })).toThrow(
      'EXPO_PUBLIC_API_URL must use https outside development',
    );
  });

  it('refuses a missing url outside development', () => {
    expect(() => resolveApiUrl({ url: undefined, isDevelopment: false, isWeb: false })).toThrow(
      'EXPO_PUBLIC_API_URL must use https outside development',
    );
  });

  it('refuses a malformed url outside development', () => {
    expect(() => resolveApiUrl({ url: 'not a url', isDevelopment: false, isWeb: false })).toThrow(
      'EXPO_PUBLIC_API_URL must use https outside development',
    );
  });

  it('does not leak the url in the error message', () => {
    let message = '';
    try {
      resolveApiUrl({ url: 'http://user:secret@api.example.org', isDevelopment: false, isWeb: false });
    } catch (error) {
      message = (error as Error).message;
    }
    expect(message).not.toContain('secret');
    expect(message).not.toBe('');
  });

  it('uses the same origin on web when no url is set', () => {
    expect(resolveApiUrl({ url: undefined, isDevelopment: false, isWeb: true })).toBe('');
  });

  it('uses the same origin on web when the url is empty', () => {
    expect(resolveApiUrl({ url: '', isDevelopment: false, isWeb: true })).toBe('');
  });

  it('accepts an https url on web outside development', () => {
    expect(resolveApiUrl({ url: 'https://api.example.org', isDevelopment: false, isWeb: true })).toBe(
      'https://api.example.org',
    );
  });

  it('refuses a plain http url on web outside development', () => {
    expect(() => resolveApiUrl({ url: 'http://api.example.org', isDevelopment: false, isWeb: true })).toThrow(
      'EXPO_PUBLIC_API_URL must use https outside development',
    );
  });
});
