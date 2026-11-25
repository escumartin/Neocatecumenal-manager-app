import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type Room = components['schemas']['Room'];
export type RoomOccupancy = components['schemas']['RoomOccupancy'];
export type OccupiedSlot = components['schemas']['OccupiedSlot'];
export type RoomFieldError =
  components['schemas']['ValidationProblem']['errors'][number];

export type RoomsFailure = {
  ok: false;
  status: number;
  code?: string;
  fieldErrors?: RoomFieldError[];
  activeBookings?: number;
};

export type ListRoomsResult =
  { ok: true; rooms: Room[] } | { ok: false; status: number };
export type RoomResult = { ok: true; room: Room } | RoomsFailure;
export type DeactivateRoomResult = { ok: true } | RoomsFailure;
export type RoomOccupancyResult =
  { ok: true; occupancy: RoomOccupancy } | RoomsFailure;

export type RoomsApi = {
  list: () => Promise<ListRoomsResult>;
  create: (name: string) => Promise<RoomResult>;
  rename: (roomId: string, name: string) => Promise<RoomResult>;
  deactivate: (roomId: string) => Promise<DeactivateRoomResult>;
  occupancy: (
    roomId: string,
    date: string,
    communityId?: string,
  ) => Promise<RoomOccupancyResult>;
};

function failure(error: unknown, response: Response): RoomsFailure {
  const problem = error as
    | { code?: string; errors?: RoomFieldError[]; activeBookings?: number }
    | undefined;
  return {
    ok: false,
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.errors ? { fieldErrors: problem.errors } : {}),
    ...(problem?.activeBookings !== undefined
      ? { activeBookings: problem.activeBookings }
      : {}),
  };
}

export function createRoomsApi(client: ApiClient): RoomsApi {
  return {
    async list() {
      const { data, response } = await client.GET('/api/rooms');
      return data
        ? { ok: true, rooms: data }
        : { ok: false, status: response.status };
    },
    async create(name) {
      const { data, error, response } = await client.POST('/api/rooms', {
        body: { name },
      });
      return data ? { ok: true, room: data } : failure(error, response);
    },
    async rename(roomId, name) {
      const { data, error, response } = await client.PUT(
        '/api/rooms/{roomId}',
        {
          params: { path: { roomId } },
          body: { name },
        },
      );
      return data ? { ok: true, room: data } : failure(error, response);
    },
    async deactivate(roomId) {
      const { error, response } = await client.POST(
        '/api/rooms/{roomId}/deactivation',
        {
          params: { path: { roomId } },
        },
      );
      return response.ok ? { ok: true } : failure(error, response);
    },
    async occupancy(roomId, date, communityId) {
      const { data, error, response } = await client.GET(
        '/api/rooms/{roomId}/occupancy',
        {
          params: {
            path: { roomId },
            query: { date, ...(communityId ? { communityId } : {}) },
          },
        },
      );
      return data ? { ok: true, occupancy: data } : failure(error, response);
    },
  };
}
