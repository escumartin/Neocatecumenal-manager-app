import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useMembers } from '../use-members';
import { ana, fakeMembersApi, luis } from '@/screens/members/support/test-support';

function deferred() {
  let resolve: (value: unknown) => void = () => undefined;
  const promise = new Promise((r) => (resolve = r));
  return { promise, resolve };
}

describe('useMembers', () => {
  it('loads the members of the community', async () => {
    const api = fakeMembersApi();

    const { result } = await renderHook(() => useMembers('community-a', api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.members).toHaveLength(3);
    expect(api.list).toHaveBeenCalledWith('community-a');
  });

  it('does not load while disabled', async () => {
    const api = fakeMembersApi();

    const { result } = await renderHook(() => useMembers(undefined, api, false));

    expect(result.current.status).toBe('idle');
    expect(api.list).not.toHaveBeenCalled();
  });

  it('reports an error when the request is refused or fails', async () => {
    const refused = fakeMembersApi({ list: jest.fn().mockResolvedValue({ ok: false, status: 403 }) });
    const failing = fakeMembersApi({ list: jest.fn().mockRejectedValue(new Error('network down')) });

    const first = await renderHook(() => useMembers(undefined, refused));
    const second = await renderHook(() => useMembers(undefined, failing));

    await waitFor(() => expect(first.result.current.status).toBe('error'));
    await waitFor(() => expect(second.result.current.status).toBe('error'));
  });

  it('reports a deactivated community apart from other errors', async () => {
    const deactivated = fakeMembersApi({
      list: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'COMMUNITY_DEACTIVATED' }),
    });

    const { result } = await renderHook(() => useMembers(undefined, deactivated));

    await waitFor(() => expect(result.current.status).toBe('deactivated'));
    expect(result.current.members).toEqual([]);
  });

  it('shows loading and no stale members when the community changes', async () => {
    const api = fakeMembersApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, members: [ana] })
        .mockReturnValueOnce(new Promise(() => undefined)),
    });
    const { result, rerender } = await renderHook(({ id }: { id: string }) => useMembers(id, api), { initialProps: { id: 'a' } });
    await waitFor(() => expect(result.current.members).toEqual([ana]));

    await rerender({ id: 'b' });

    expect(result.current.status).toBe('loading');
    expect(result.current.members).toEqual([]);
  });

  it('reloads and refreshes silently', async () => {
    const api = fakeMembersApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, members: [ana] })
        .mockResolvedValueOnce({ ok: true, members: [luis] })
        .mockResolvedValueOnce({ ok: false, status: 500 }),
    });
    const { result } = await renderHook(() => useMembers(undefined, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.reload();
    });
    expect(result.current.members).toEqual([luis]);

    await act(async () => {
      await result.current.refresh();
    });
    expect(result.current.members).toEqual([luis]);
    expect(result.current.status).toBe('ready');
  });

  it('ignores a stale refresh that resolves after a newer reload', async () => {
    const stale = deferred();
    const api = fakeMembersApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, members: [ana] })
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValueOnce({ ok: true, members: [luis] }),
    });
    const { result } = await renderHook(() => useMembers(undefined, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });
    await act(() => result.current.reload());
    await act(async () => {
      stale.resolve({ ok: true, members: [ana, luis] });
      await refreshing;
    });

    expect(result.current.members).toEqual([luis]);
  });

  it('ignores the response of the previous community after the community changes', async () => {
    const slow = deferred();
    const api = fakeMembersApi({
      list: jest
        .fn()
        .mockReturnValueOnce(slow.promise)
        .mockResolvedValueOnce({ ok: true, members: [luis] }),
    });
    const { result, rerender } = await renderHook(({ id }: { id: string }) => useMembers(id, api), {
      initialProps: { id: 'a' },
    });

    await rerender({ id: 'b' });
    await waitFor(() => expect(result.current.members).toEqual([luis]));
    await act(async () => {
      slow.resolve({ ok: true, members: [ana] });
    });

    expect(result.current.members).toEqual([luis]);
  });
});
