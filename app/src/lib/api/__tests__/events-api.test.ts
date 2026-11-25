import { createApiClient } from '../client';
import { createEventsApi } from '../events-api';

const event = {
  id: '6f1c1f1e-0000-4000-8000-000000000051',
  type: 'WORD',
  title: 'Palabra de apertura',
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  roomId: '6f1c1f1e-0000-4000-8000-000000000031',
  date: '2030-05-11',
  startTime: '18:00',
  endTime: '20:00',
  bookingRequestId: '6f1c1f1e-0000-4000-8000-000000000041',
  bookingStatus: 'PENDING',
};

const input = {
  type: 'WORD' as const,
  title: event.title,
  roomId: event.roomId,
  date: event.date,
  startTime: event.startTime,
  endTime: event.endTime,
};

function json(status: number, body: unknown, type = 'application/json') {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': type },
  });
}

function apiWith(response: Response) {
  const fetch = jest.fn().mockResolvedValue(response);
  const client = createApiClient({
    baseUrl: 'http://localhost',
    readToken: async () => null,
    onUnauthorized: jest.fn(),
    onPendingStep: jest.fn(),
    fetch,
  });
  return {
    api: createEventsApi(client),
    sent: () => fetch.mock.calls[0][0] as Request,
  };
}

describe('events api', () => {
  it('creates an event with POST /api/events', async () => {
    const { api, sent } = apiWith(json(201, event));

    const result = await api.create({ ...input, communityId: event.communityId });

    expect(result).toEqual({ ok: true, event });
    expect(sent().method).toBe('POST');
    expect(sent().url).toBe('http://localhost/api/events');
    expect(await sent().json()).toEqual({ ...input, communityId: event.communityId });
  });

  it('reports the field errors of a rejected event', async () => {
    const { api } = apiWith(
      json(
        422,
        { code: 'VALIDATION_FAILED', errors: [{ field: 'title', code: 'TOO_LONG' }] },
        'application/problem+json',
      ),
    );

    const result = await api.create({ ...input, title: 'x'.repeat(121) });

    expect(result).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'title', code: 'TOO_LONG' }],
    });
  });

  it('reports every conflicting date of an occupied room', async () => {
    const conflicts = [{ date: '2030-05-11', startTime: '18:00', endTime: '20:00' }];
    const { api } = apiWith(json(409, { code: 'ROOM_OCCUPIED', conflicts }, 'application/problem+json'));

    const result = await api.create(input);

    expect(result).toEqual({ ok: false, status: 409, code: 'ROOM_OCCUPIED', conflicts });
  });

  it('reports a room that was deactivated', async () => {
    const { api } = apiWith(json(409, { code: 'ROOM_INACTIVE' }, 'application/problem+json'));

    expect(await api.create(input)).toEqual({ ok: false, status: 409, code: 'ROOM_INACTIVE' });
  });

  it('reports an unknown room as not found', async () => {
    const { api } = apiWith(json(404, { code: 'NOT_FOUND' }, 'application/problem+json'));

    expect(await api.create(input)).toEqual({ ok: false, status: 404, code: 'NOT_FOUND' });
  });

  it('gets an event with GET /api/events/{eventId}', async () => {
    const { api, sent } = apiWith(json(200, event));

    const result = await api.get(event.id);

    expect(result).toEqual({ ok: true, event });
    expect(sent().method).toBe('GET');
    expect(sent().url).toBe(`http://localhost/api/events/${event.id}`);
  });

  it('reports an event of another community as forbidden', async () => {
    const { api } = apiWith(json(403, { code: 'FORBIDDEN' }, 'application/problem+json'));

    expect(await api.get(event.id)).toEqual({ ok: false, status: 403, code: 'FORBIDDEN' });
  });

  it('reports an unknown event as not found', async () => {
    const { api } = apiWith(json(404, { code: 'NOT_FOUND' }, 'application/problem+json'));

    expect(await api.get(event.id)).toEqual({ ok: false, status: 404, code: 'NOT_FOUND' });
  });
});
