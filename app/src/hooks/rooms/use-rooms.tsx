import { useCallback, useEffect, useState } from 'react';
import { roomsApi } from '@/lib/api';
import type { ListRoomsResult, Room, RoomsApi } from '@/lib/api/rooms-api';

export type RoomsStatus = 'idle' | 'loading' | 'ready' | 'error';

type State = { status: RoomsStatus; rooms: Room[] };

function toState(result: ListRoomsResult): State {
  return result.ok ? { status: 'ready', rooms: result.rooms } : { status: 'error', rooms: [] };
}

const FAILED: State = { status: 'error', rooms: [] };

export function useRooms(api: RoomsApi = roomsApi, enabled = true) {
  const [state, setState] = useState<State>({
    status: enabled ? 'loading' : 'idle',
    rooms: [],
  });

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .list()
      .then(toState, () => FAILED)
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, enabled]);

  const reload = useCallback(async () => {
    setState((current) => ({ ...current, status: 'loading' }));
    try {
      setState(toState(await api.list()));
    } catch {
      setState(FAILED);
    }
  }, [api]);

  const create = useCallback(
    async (name: string) => {
      const result = await api.create(name);
      if (result.ok) {
        setState((current) => ({
          ...current,
          rooms: [...current.rooms, result.room],
        }));
      }
      return result;
    },
    [api],
  );

  const rename = useCallback(
    async (roomId: string, name: string) => {
      const result = await api.rename(roomId, name);
      if (result.ok) {
        setState((current) => ({
          ...current,
          rooms: current.rooms.map((room) => (room.id === roomId ? result.room : room)),
        }));
      }
      return result;
    },
    [api],
  );

  const deactivate = useCallback(
    async (roomId: string) => {
      const result = await api.deactivate(roomId);
      if (result.ok) {
        setState((current) => ({
          ...current,
          rooms: current.rooms.filter((room) => room.id !== roomId),
        }));
      }
      return result;
    },
    [api],
  );

  return {
    status: state.status,
    rooms: state.rooms,
    reload,
    create,
    rename,
    deactivate,
  };
}
