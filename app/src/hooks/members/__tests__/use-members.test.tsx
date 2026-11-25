import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useMembers } from '../use-members';
import { ana, fakeMembersApi, luis } from '@/screens/members/support/test-support';

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
});
