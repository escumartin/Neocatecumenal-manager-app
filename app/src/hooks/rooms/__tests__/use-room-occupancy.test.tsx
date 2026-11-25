import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useRoomOccupancy } from '../use-room-occupancy';
import type { RoomsApi } from '@/lib/api/rooms-api';

const roomId = '6f1c1f1e-0000-4000-8000-000000000031';
const slot = {
  startTime: '18:00',
  endTime: '20:00',
  status: 'PENDING' as const,
  ownCommunity: true,
};
const occupancy = { roomId, date: '2030-05-11', occupied: [slot] };
const query = { roomId, date: '2030-05-11' };

function fakeApi(overrides: Partial<RoomsApi> = {}): RoomsApi {
  return {
    list: jest.fn(),
    create: jest.fn(),
    rename: jest.fn(),
    deactivate: jest.fn(),
    occupancy: jest.fn().mockResolvedValue({ ok: true, occupancy }),
    ...overrides,
  };
}

function deferred() {
  let resolve: (value: unknown) => void = () => undefined;
  const promise = new Promise((r) => (resolve = r));
  return { promise, resolve };
}

describe('useRoomOccupancy', () => {
  it('stays idle and does not call the server without a query', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useRoomOccupancy(api, null));

    expect(result.current.status).toBe('idle');
    expect(result.current.occupied).toEqual([]);
    expect(api.occupancy).not.toHaveBeenCalled();
  });

  it('loads the occupied slots of the room and day', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useRoomOccupancy(api, query));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.occupied).toEqual([slot]);
    expect(api.occupancy).toHaveBeenCalledWith(roomId, '2030-05-11', undefined);
  });

  it('sends the community when the query has one', async () => {
    const api = fakeApi();
    const communityId = '6f1c1f1e-0000-4000-8000-000000000012';

    await renderHook(() => useRoomOccupancy(api, { ...query, communityId }));

    await waitFor(() => expect(api.occupancy).toHaveBeenCalledWith(roomId, '2030-05-11', communityId));
  });

  it('reports an error when the occupancy cannot be loaded', async () => {
    const api = fakeApi({
      occupancy: jest.fn().mockResolvedValue({ ok: false, status: 500 }),
    });

    const { result } = await renderHook(() => useRoomOccupancy(api, query));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.occupied).toEqual([]);
  });

  it('reloads the occupancy on demand', async () => {
    const occupancyCall = jest
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        occupancy: { ...occupancy, occupied: [] },
      })
      .mockResolvedValueOnce({ ok: true, occupancy });
    const api = fakeApi({ occupancy: occupancyCall });
    const { result } = await renderHook(() => useRoomOccupancy(api, query));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.reload();
    });

    expect(result.current.occupied).toEqual([slot]);
  });

  it('loads again when the day changes', async () => {
    const api = fakeApi();
    const { result, rerender } = await renderHook(
      ({ date }: { date: string }) => useRoomOccupancy(api, { roomId, date }),
      { initialProps: { date: '2030-05-11' } },
    );
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await rerender({ date: '2030-05-12' });

    await waitFor(() => expect(api.occupancy).toHaveBeenCalledWith(roomId, '2030-05-12', undefined));
  });

  it('reports an error when the occupancy request fails', async () => {
    const api = fakeApi({ occupancy: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await renderHook(() => useRoomOccupancy(api, query));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.occupied).toEqual([]);
  });

  it('reports an error when a reload request fails', async () => {
    const api = fakeApi({
      occupancy: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, occupancy })
        .mockRejectedValueOnce(new Error('network down')),
    });
    const { result } = await renderHook(() => useRoomOccupancy(api, query));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.reload());

    expect(result.current.status).toBe('error');
  });

  it('keeps the slots and the ready status while a silent refresh is pending', async () => {
    let resolveRefresh: (value: unknown) => void = () => undefined;
    const other = { ...slot, startTime: '20:00', endTime: '21:00' };
    const api = fakeApi({
      occupancy: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, occupancy })
        .mockReturnValueOnce(new Promise((resolve) => (resolveRefresh = resolve))),
    });
    const { result } = await renderHook(() => useRoomOccupancy(api, query));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.occupied).toEqual([slot]);

    await act(async () => {
      resolveRefresh({ ok: true, occupancy: { ...occupancy, occupied: [other] } });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.occupied).toEqual([other]);
  });

  it('keeps the slots when a silent refresh fails', async () => {
    const api = fakeApi({
      occupancy: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, occupancy })
        .mockResolvedValueOnce({ ok: false, status: 500 }),
    });
    const { result } = await renderHook(() => useRoomOccupancy(api, query));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.status).toBe('ready');
    expect(result.current.occupied).toEqual([slot]);
  });

  it('keeps the slots when a silent refresh rejects', async () => {
    const api = fakeApi({
      occupancy: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, occupancy })
        .mockRejectedValueOnce(new Error('network down')),
    });
    const { result } = await renderHook(() => useRoomOccupancy(api, query));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.status).toBe('ready');
    expect(result.current.occupied).toEqual([slot]);
  });

  it('ignores a stale refresh of the previous date after the query changes', async () => {
    const stale = deferred();
    const next = { ...slot, startTime: '10:00', endTime: '11:00' };
    const api = fakeApi({
      occupancy: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, occupancy })
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValueOnce({ ok: true, occupancy: { ...occupancy, date: '2030-05-12', occupied: [next] } }),
    });
    const { result, rerender } = await renderHook(
      ({ date }: { date: string }) => useRoomOccupancy(api, { roomId, date }),
      { initialProps: { date: '2030-05-11' } },
    );
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });
    await rerender({ date: '2030-05-12' });
    await waitFor(() => expect(result.current.occupied).toEqual([next]));
    await act(async () => {
      stale.resolve({ ok: true, occupancy });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.occupied).toEqual([next]);
  });

  it('ignores a stale refresh that resolves after a newer reload', async () => {
    const stale = deferred();
    const newest = { ...slot, startTime: '12:00', endTime: '13:00' };
    const api = fakeApi({
      occupancy: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, occupancy })
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValueOnce({ ok: true, occupancy: { ...occupancy, occupied: [newest] } }),
    });
    const { result } = await renderHook(() => useRoomOccupancy(api, query));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });
    await act(() => result.current.reload());
    await act(async () => {
      stale.resolve({ ok: true, occupancy });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.occupied).toEqual([newest]);
  });
});
