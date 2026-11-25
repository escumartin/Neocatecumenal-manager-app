import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type AgendaEvent = components['schemas']['Event'];
export type EventType = components['schemas']['EventType'];
export type CreateEventInput = components['schemas']['CreateEvent'];
export type EventFieldError = components['schemas']['ValidationProblem']['errors'][number];
export type EventConflict = NonNullable<components['schemas']['RoomOccupiedProblem']['conflicts']>[number];

export type EventFailure = {
  ok: false;
  status: number;
  code?: string;
  fieldErrors?: EventFieldError[];
  conflicts?: EventConflict[];
};

export type EventResult = { ok: true; event: AgendaEvent } | EventFailure;

export type EventsApi = {
  create: (input: CreateEventInput) => Promise<EventResult>;
  get: (eventId: string) => Promise<EventResult>;
};

function failure(error: unknown, response: Response): EventFailure {
  const problem = error as
    | {
        code?: string;
        errors?: EventFieldError[];
        conflicts?: EventConflict[];
      }
    | undefined;
  return {
    ok: false,
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.errors ? { fieldErrors: problem.errors } : {}),
    ...(problem?.conflicts ? { conflicts: problem.conflicts } : {}),
  };
}

export function createEventsApi(client: ApiClient): EventsApi {
  return {
    async create(input) {
      const { data, error, response } = await client.POST('/api/events', { body: input });
      return data ? { ok: true, event: data } : failure(error, response);
    },
    async get(eventId) {
      const { data, error, response } = await client.GET('/api/events/{eventId}', {
        params: { path: { eventId } },
      });
      return data ? { ok: true, event: data } : failure(error, response);
    },
  };
}
