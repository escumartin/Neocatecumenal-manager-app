import { act, renderHook, waitFor } from '@testing-library/react-native';
import { AppState } from 'react-native';
import { useAppCompatibility } from '../use-app-compatibility';
import type { ServiceApi } from '@/lib/api/service-api';

function fakeApi(minimumAppVersion = '1.2.0'): ServiceApi {
  return {
    getCompatibility: jest.fn().mockResolvedValue({
      ok: true,
      compatibility: { minimumAppVersion, serviceRevision: '86a5aa6' },
    }),
  };
}

describe('useAppCompatibility', () => {
  let listeners: ((state: string) => void)[];
  let remove: jest.Mock;

  beforeEach(() => {
    listeners = [];
    remove = jest.fn();
    jest.spyOn(AppState, 'addEventListener').mockImplementation((_type, listener) => {
      listeners.push(listener as (state: string) => void);
      return { remove };
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('requires an update when the app is below the minimum', async () => {
    const { result } = await renderHook(() =>
      useAppCompatibility({ api: fakeApi('1.2.0'), currentVersion: '1.1.0', platform: 'ios' }),
    );

    await waitFor(() => expect(result.current.updateRequired).toBe(true));
  });

  it('does not require an update when the version equals the minimum', async () => {
    const api = fakeApi('1.2.0');
    const { result } = await renderHook(() =>
      useAppCompatibility({ api, currentVersion: '1.2.0', platform: 'android' }),
    );

    await waitFor(() => expect(api.getCompatibility).toHaveBeenCalledTimes(1));
    expect(result.current.updateRequired).toBe(false);
  });

  it('does not require an update when the version is above the minimum', async () => {
    const api = fakeApi('1.2.0');
    const { result } = await renderHook(() =>
      useAppCompatibility({ api, currentVersion: '1.10.0', platform: 'ios' }),
    );

    await waitFor(() => expect(api.getCompatibility).toHaveBeenCalledTimes(1));
    expect(result.current.updateRequired).toBe(false);
  });

  it('does not block on a service error', async () => {
    const api: ServiceApi = { getCompatibility: jest.fn().mockResolvedValue({ ok: false, status: 503 }) };
    const { result } = await renderHook(() =>
      useAppCompatibility({ api, currentVersion: '1.0.0', platform: 'ios' }),
    );

    await waitFor(() => expect(api.getCompatibility).toHaveBeenCalledTimes(1));
    expect(result.current.updateRequired).toBe(false);
  });

  it('does not block on a network error', async () => {
    const api: ServiceApi = { getCompatibility: jest.fn().mockRejectedValue(new TypeError('Network request failed')) };
    const { result } = await renderHook(() =>
      useAppCompatibility({ api, currentVersion: '1.0.0', platform: 'ios' }),
    );

    await waitFor(() => expect(api.getCompatibility).toHaveBeenCalledTimes(1));
    expect(result.current.updateRequired).toBe(false);
  });

  it('does not query the service on web', async () => {
    const api = fakeApi('9.0.0');
    const { result } = await renderHook(() =>
      useAppCompatibility({ api, currentVersion: '1.0.0', platform: 'web' }),
    );

    expect(api.getCompatibility).not.toHaveBeenCalled();
    expect(result.current.updateRequired).toBe(false);
  });

  it('queries again when the app returns to the foreground', async () => {
    const api = fakeApi('1.2.0');
    await renderHook(() => useAppCompatibility({ api, currentVersion: '1.0.0', platform: 'ios' }));
    await waitFor(() => expect(api.getCompatibility).toHaveBeenCalledTimes(1));

    await act(async () => {
      listeners.forEach((listener) => listener('active'));
    });

    await waitFor(() => expect(api.getCompatibility).toHaveBeenCalledTimes(2));
  });

  it('does not query again when the app goes to the background', async () => {
    const api = fakeApi('1.2.0');
    await renderHook(() => useAppCompatibility({ api, currentVersion: '1.0.0', platform: 'ios' }));
    await waitFor(() => expect(api.getCompatibility).toHaveBeenCalledTimes(1));

    await act(async () => {
      listeners.forEach((listener) => listener('background'));
    });

    expect(api.getCompatibility).toHaveBeenCalledTimes(1);
  });

  it('stops listening to the app state when unmounted', async () => {
    const api = fakeApi();
    const { unmount } = await renderHook(() =>
      useAppCompatibility({ api, currentVersion: '1.0.0', platform: 'ios' }),
    );

    await unmount();

    expect(remove).toHaveBeenCalled();
  });
});
