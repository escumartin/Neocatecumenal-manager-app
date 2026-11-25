import { useCallback, useEffect, useRef, useState } from 'react';
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
  const sequence = useRef(0);
  const keyRef = useRef(key);

  useEffect(() => {
    keyRef.current = key;
  }, [key]);

  useEffect(() => {
    const id = ++sequence.current;
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
        if (active && id === sequence.current) {
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
    const id = ++sequence.current;
    setState((current) => ({ ...current, key, status: 'loading' }));
    let next = failed(key);
    try {
      next = toState(key, await api.occupancy(roomId, date, communityId));
    } catch {
      next = failed(key);
    }
    if (id === sequence.current && keyRef.current === key) {
      setState(next);
    }
  }, [api, key, roomId, date, communityId]);

  const refresh = useCallback(async () => {
    if (key === null || roomId === undefined || date === undefined) {
      return;
    }
    const id = ++sequence.current;
    try {
      const result = await api.occupancy(roomId, date, communityId);
      if (result.ok && id === sequence.current && keyRef.current === key) {
        setState(toState(key, result));
      }
    } catch {
      return;
    }
  }, [api, key, roomId, date, communityId]);

  if (key === null) {
    return { status: IDLE.status, occupied: IDLE.occupied, reload, refresh };
  }
  if (state.key !== key) {
    return { status: 'loading' as const, occupied: [] as OccupiedSlot[], reload, refresh };
  }
  return { status: state.status, occupied: state.occupied, reload, refresh };
}
