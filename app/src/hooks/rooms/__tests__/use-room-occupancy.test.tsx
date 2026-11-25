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
});
