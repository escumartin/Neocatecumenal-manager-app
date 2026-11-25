import { useCallback, useEffect, useState } from 'react';
import { roomsApi } from '@/lib/api';
import type { OccupiedSlot, RoomOccupancyResult, RoomsApi } from '@/lib/api/rooms-api';

export type RoomOccupancyStatus = 'idle' | 'loading' | 'ready' | 'error';

export type RoomOccupancyQuery = { roomId: string; date: string; communityId?: string };

type State = { key: string | null; status: RoomOccupancyStatus; occupied: OccupiedSlot[] };

const IDLE: State = { key: null, status: 'idle', occupied: [] };

function toState(key: string, result: RoomOccupancyResult): State {
  return result.ok
    ? { key, status: 'ready', occupied: result.occupancy.occupied }
    : { key, status: 'error', occupied: [] };
}

function failed(key: string): State {
  return { key, status: 'error', occupied: [] };
}

export function useRoomOccupancy(api: RoomsApi = roomsApi, query: RoomOccupancyQuery | null) {
  const roomId = query?.roomId;
  const date = query?.date;
  const communityId = query?.communityId;
  const key = roomId === undefined || date === undefined ? null : `${roomId}|${date}|${communityId ?? ''}`;
  const [state, setState] = useState<State>(IDLE);

  useEffect(() => {
    if (key === null || roomId === undefined || date === undefined) {
      return;
    }
    let active = true;
    api
      .occupancy(roomId, date, communityId)
      .then(
        (result) => toState(key, result),
        () => failed(key),
      )
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, key, roomId, date, communityId]);

  const reload = useCallback(async () => {
    if (key === null || roomId === undefined || date === undefined) {
      return;
    }
    setState((current) => ({ ...current, key, status: 'loading' }));
    try {
      setState(toState(key, await api.occupancy(roomId, date, communityId)));
    } catch {
      setState(failed(key));
    }
  }, [api, key, roomId, date, communityId]);

  if (key === null) {
    return { status: IDLE.status, occupied: IDLE.occupied, reload };
  }
  if (state.key !== key) {
    return { status: 'loading' as const, occupied: [] as OccupiedSlot[], reload };
  }
  return { status: state.status, occupied: state.occupied, reload };
}
