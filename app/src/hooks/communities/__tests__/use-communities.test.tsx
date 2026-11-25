import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useCommunities } from '../use-communities';
import type { CommunitiesApi } from '@/lib/api/communities-api';

const communities = [
  { id: '6f1c1f1e-0000-4000-8000-000000000011', number: 1 },
  { id: '6f1c1f1e-0000-4000-8000-000000000012', number: 2 },
];

describe('useCommunities', () => {
  it('loads the communities in the order the server returns them', async () => {
    const api: CommunitiesApi = { list: jest.fn().mockResolvedValue({ ok: true, communities }) };

    const { result } = await renderHook(() => useCommunities(api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.communities).toEqual(communities);
  });

  it('reports an error when the list cannot be loaded', async () => {
    const api: CommunitiesApi = { list: jest.fn().mockResolvedValue({ ok: false, status: 500 }) };

    const { result } = await renderHook(() => useCommunities(api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.communities).toEqual([]);
  });

  it('does not call the server when disabled', async () => {
    const api: CommunitiesApi = { list: jest.fn() };

    const { result } = await renderHook(() => useCommunities(api, false));

    expect(result.current.status).toBe('idle');
    expect(api.list).not.toHaveBeenCalled();
  });

  it('reloads on demand', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, communities });
    const api: CommunitiesApi = { list };
    const { result } = await renderHook(() => useCommunities(api));
    await waitFor(() => expect(result.current.status).toBe('error'));

    await act(async () => {
      await result.current.reload();
    });

    await waitFor(() => expect(result.current.status).toBe('ready'));
  });

  it('reports an error when the list request fails', async () => {
    const api: CommunitiesApi = { list: jest.fn().mockRejectedValue(new Error('network down')) };

    const { result } = await renderHook(() => useCommunities(api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.communities).toEqual([]);
  });
});

describe('useCommunities with deactivated communities', () => {
  it('asks only for the active communities by default', async () => {
    const api: CommunitiesApi = { list: jest.fn().mockResolvedValue({ ok: true, communities }) };

    const { result } = await renderHook(() => useCommunities(api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(api.list).toHaveBeenCalledWith(undefined);
  });

  it('asks for the deactivated communities too when includeDeactivated is set', async () => {
    const api: CommunitiesApi = { list: jest.fn().mockResolvedValue({ ok: true, communities }) };

    const { result } = await renderHook(() => useCommunities(api, true, { includeDeactivated: true }));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(api.list).toHaveBeenCalledWith({ includeDeactivated: true });
  });

  it('keeps asking for the deactivated communities when it reloads', async () => {
    const api: CommunitiesApi = { list: jest.fn().mockResolvedValue({ ok: true, communities }) };
    const { result } = await renderHook(() => useCommunities(api, true, { includeDeactivated: true }));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.reload();
    });

    expect(api.list).toHaveBeenLastCalledWith({ includeDeactivated: true });
  });
});

describe('useCommunities refresh', () => {
  it('replaces the list without going through the loading state', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: true, communities: [communities[0]] })
      .mockResolvedValueOnce({ ok: true, communities });
    const api: CommunitiesApi = { list };
    const { result } = await renderHook(() => useCommunities(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.communities).toEqual(communities);
  });

  it('keeps the current list when the refresh fails', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: true, communities })
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockRejectedValueOnce(new Error('network down'));
    const api: CommunitiesApi = { list };
    const { result } = await renderHook(() => useCommunities(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.refresh();
      await result.current.refresh();
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.communities).toEqual(communities);
  });

  it('does nothing when disabled', async () => {
    const list = jest.fn();
    const api: CommunitiesApi = { list };
    const { result } = await renderHook(() => useCommunities(api, false));

    await act(async () => {
      await result.current.refresh();
    });

    expect(list).not.toHaveBeenCalled();
  });
});
