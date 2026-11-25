import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useUsers } from '../use-users';
import type { UsersApi } from '@/lib/api/users-api';

const communityId = '6f1c1f1e-0000-4000-8000-000000000011';
const first = { id: '6f1c1f1e-0000-4000-8000-000000000021', accessId: 482913, name: 'Ana', role: 'REGISTERED' as const, communityId, passwordChangePending: false };
const second = { ...first, id: '6f1c1f1e-0000-4000-8000-000000000022', accessId: 482914, name: 'Luis' };

function fakeApi(overrides: Partial<UsersApi> = {}): UsersApi {
  return {
    list: jest.fn().mockResolvedValue({ ok: true, users: [first, second] }),
    get: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    remove: jest.fn(),
    resetPassword: jest.fn(),
    ...overrides,
  };
}

function deferred() {
  let resolve: (value: unknown) => void = () => undefined;
  const promise = new Promise((r) => (resolve = r));
  return { promise, resolve };
}

describe('useUsers', () => {
  it('loads the users of the community', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useUsers(communityId, api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.users).toEqual([first, second]);
    expect(api.list).toHaveBeenCalledWith(communityId);
  });

  it('reports an error when the list cannot be loaded', async () => {
    const api = fakeApi({ list: jest.fn().mockResolvedValue({ ok: false, status: 500 }) });

    const { result } = await renderHook(() => useUsers(communityId, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.users).toEqual([]);
  });

  it('does not call the server when disabled', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useUsers(communityId, api, false));

    expect(result.current.status).toBe('idle');
    expect(api.list).not.toHaveBeenCalled();
  });

  it('reloads the list when the community changes', async () => {
    const api = fakeApi();
    const { rerender } = await renderHook(({ id }: { id: string }) => useUsers(id, api), {
      initialProps: { id: communityId },
    });

    await rerender({ id: '6f1c1f1e-0000-4000-8000-000000000012' });

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith('6f1c1f1e-0000-4000-8000-000000000012'));
  });

  it('drops a user from the list after deleting it', async () => {
    const api = fakeApi({ remove: jest.fn().mockResolvedValue({ ok: true }) });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.remove(first.id);
    });

    expect(outcome).toEqual({ ok: true });
    expect(result.current.users).toEqual([second]);
  });

  it('keeps the user in the list when the deletion is refused', async () => {
    const refusal = { ok: false, status: 409, code: 'LAST_MODERATOR' };
    const api = fakeApi({ remove: jest.fn().mockResolvedValue(refusal) });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.remove(first.id);
    });

    expect(outcome).toEqual(refusal);
    expect(result.current.users).toEqual([first, second]);
  });

  it('delegates creation to the api', async () => {
    const created = { ...first, initialPassword: 'abcd234567' };
    const api = fakeApi({ create: jest.fn().mockResolvedValue({ ok: true, user: created }) });
    const { result } = await renderHook(() => useUsers(communityId, api));

    let outcome;
    await act(async () => {
      outcome = await result.current.create({ name: 'Ana', role: 'REGISTERED', communityId });
    });

    expect(outcome).toEqual({ ok: true, user: created });
    expect(api.create).toHaveBeenCalledWith({ name: 'Ana', role: 'REGISTERED', communityId });
  });

  it('keeps the data and the ready status while a silent refresh is pending', async () => {
    let resolveRefresh: (value: unknown) => void = () => undefined;
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, users: [first] })
        .mockReturnValueOnce(new Promise((resolve) => (resolveRefresh = resolve))),
    });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.users).toEqual([first]);

    await act(async () => {
      resolveRefresh({ ok: true, users: [second] });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.users).toEqual([second]);
  });

  it('keeps the data when a silent refresh fails', async () => {
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, users: [first] })
        .mockResolvedValueOnce({ ok: false, status: 500 }),
    });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.status).toBe('ready');
    expect(result.current.users).toEqual([first]);
  });

  it('keeps the data when a silent refresh rejects', async () => {
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, users: [first] })
        .mockRejectedValueOnce(new Error('network down')),
    });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.status).toBe('ready');
    expect(result.current.users).toEqual([first]);
  });

  it('ignores a stale refresh of the previous community after the community changes', async () => {
    const otherCommunity = '6f1c1f1e-0000-4000-8000-000000000012';
    const stale = deferred();
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, users: [first] })
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValueOnce({ ok: true, users: [second] }),
    });
    const { result, rerender } = await renderHook(({ id }: { id: string }) => useUsers(id, api), {
      initialProps: { id: communityId },
    });
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });
    await rerender({ id: otherCommunity });
    await waitFor(() => expect(result.current.users).toEqual([second]));
    await act(async () => {
      stale.resolve({ ok: true, users: [first] });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.users).toEqual([second]);
  });

  it('ignores a stale refresh that resolves after a newer reload', async () => {
    const stale = deferred();
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, users: [first] })
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValueOnce({ ok: true, users: [second] }),
    });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });
    await act(() => result.current.reload());
    await act(async () => {
      stale.resolve({ ok: true, users: [first] });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.users).toEqual([second]);
  });

  it('does not call the server on refresh when disabled', async () => {
    const api = fakeApi();
    const { result } = await renderHook(() => useUsers(communityId, api, false));

    await act(() => result.current.refresh());

    expect(api.list).not.toHaveBeenCalled();
  });
});
