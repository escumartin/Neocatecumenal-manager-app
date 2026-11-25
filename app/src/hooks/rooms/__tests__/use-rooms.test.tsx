import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useRooms } from '../use-rooms';
import type { RoomsApi } from '@/lib/api/rooms-api';

const hall = {
  id: '6f1c1f1e-0000-4000-8000-000000000031',
  name: 'Salón grande',
  active: true,
};
const chapel = {
  id: '6f1c1f1e-0000-4000-8000-000000000032',
  name: 'Capilla',
  active: true,
};

function fakeApi(overrides: Partial<RoomsApi> = {}): RoomsApi {
  return {
    list: jest.fn().mockResolvedValue({ ok: true, rooms: [hall, chapel] }),
    create: jest.fn(),
    rename: jest.fn(),
    deactivate: jest.fn(),
    occupancy: jest.fn(),
    ...overrides,
  };
}

describe('useRooms', () => {
  it('loads the active rooms', async () => {
    const api = fakeApi();
    const { result } = await renderHook(() => useRooms(api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.rooms).toEqual([hall, chapel]);
  });

  it('reports an error when the list cannot be loaded', async () => {
    const api = fakeApi({
      list: jest.fn().mockResolvedValue({ ok: false, status: 500 }),
    });

    const { result } = await renderHook(() => useRooms(api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.rooms).toEqual([]);
  });

  it('reloads the list after an error', async () => {
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: false, status: 500 })
        .mockResolvedValueOnce({ ok: true, rooms: [hall] }),
    });
    const { result } = await renderHook(() => useRooms(api));
    await waitFor(() => expect(result.current.status).toBe('error'));

    await act(() => result.current.reload());

    expect(result.current.status).toBe('ready');
    expect(result.current.rooms).toEqual([hall]);
  });

  it('adds a created room to the list', async () => {
    const created = {
      id: '6f1c1f1e-0000-4000-8000-000000000033',
      name: 'Aula',
      active: true,
    };
    const api = fakeApi({
      create: jest.fn().mockResolvedValue({ ok: true, room: created }),
    });
    const { result } = await renderHook(() => useRooms(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.create('Aula');
    });

    expect(outcome).toEqual({ ok: true, room: created });
    expect(api.create).toHaveBeenCalledWith('Aula');
    expect(result.current.rooms).toEqual([hall, chapel, created]);
  });

  it('keeps the list when a creation is refused', async () => {
    const refused = {
      ok: false as const,
      status: 409,
      code: 'ROOM_NAME_TAKEN',
    };
    const api = fakeApi({ create: jest.fn().mockResolvedValue(refused) });
    const { result } = await renderHook(() => useRooms(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.create('Capilla');
    });

    expect(outcome).toEqual(refused);
    expect(result.current.rooms).toEqual([hall, chapel]);
  });

  it('replaces a renamed room in place', async () => {
    const renamed = { ...hall, name: 'Salón pequeño' };
    const api = fakeApi({
      rename: jest.fn().mockResolvedValue({ ok: true, room: renamed }),
    });
    const { result } = await renderHook(() => useRooms(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.rename(hall.id, 'Salón pequeño');
    });

    expect(api.rename).toHaveBeenCalledWith(hall.id, 'Salón pequeño');
    expect(result.current.rooms).toEqual([renamed, chapel]);
  });

  it('removes a deactivated room from the list', async () => {
    const api = fakeApi({
      deactivate: jest.fn().mockResolvedValue({ ok: true }),
    });
    const { result } = await renderHook(() => useRooms(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.deactivate(hall.id);
    });

    expect(api.deactivate).toHaveBeenCalledWith(hall.id);
    expect(result.current.rooms).toEqual([chapel]);
  });

  it('keeps the room when the deactivation is refused', async () => {
    const api = fakeApi({
      deactivate: jest.fn().mockResolvedValue({
        ok: false,
        status: 409,
        code: 'ROOM_HAS_ACTIVE_BOOKINGS',
        activeBookings: 2,
      }),
    });
    const { result } = await renderHook(() => useRooms(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.deactivate(hall.id);
    });

    expect(result.current.rooms).toEqual([hall, chapel]);
  });

  it('does not call the server when disabled', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useRooms(api, false));

    expect(result.current.status).toBe('idle');
    expect(api.list).not.toHaveBeenCalled();
  });

  it('reports an error when the list request fails', async () => {
    const api = fakeApi({ list: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await renderHook(() => useRooms(api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.rooms).toEqual([]);
  });

  it('reports an error when a reload request fails', async () => {
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, rooms: [hall] })
        .mockRejectedValueOnce(new Error('network down')),
    });
    const { result } = await renderHook(() => useRooms(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.reload());

    expect(result.current.status).toBe('error');
  });
});
