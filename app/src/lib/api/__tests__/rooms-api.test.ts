import { createApiClient } from '../client';
import { createRoomsApi } from '../rooms-api';

const room = {
  id: '6f1c1f1e-0000-4000-8000-000000000031',
  name: 'Salón grande',
  active: true,
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
    api: createRoomsApi(client),
    request: () => fetch.mock.calls[0][0] as Request,
  };
}

describe('rooms api', () => {
  it('lists the active rooms from GET /api/rooms', async () => {
    const { api, request } = apiWith(json(200, [room]));

    const result = await api.list();

    expect(result).toEqual({ ok: true, rooms: [room] });
    expect(request().method).toBe('GET');
    expect(request().url).toBe('http://localhost/api/rooms');
  });

  it('reports the status when the list is refused', async () => {
    const { api } = apiWith(json(500, {}, 'application/problem+json'));

    expect(await api.list()).toEqual({ ok: false, status: 500 });
  });

  it('creates a room with POST /api/rooms', async () => {
    const { api, request } = apiWith(json(201, room));

    const result = await api.create('Salón grande');

    expect(result).toEqual({ ok: true, room });
    expect(request().method).toBe('POST');
    expect(request().url).toBe('http://localhost/api/rooms');
    expect(await request().json()).toEqual({ name: 'Salón grande' });
  });

  it('reports the field errors of a rejected creation', async () => {
    const { api } = apiWith(
      json(
        422,
        {
          code: 'VALIDATION_FAILED',
          errors: [{ field: 'name', code: 'REQUIRED' }],
        },
        'application/problem+json',
      ),
    );

    expect(await api.create('')).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'name', code: 'REQUIRED' }],
    });
  });

  it('reports a repeated name', async () => {
    const { api } = apiWith(
      json(409, { code: 'ROOM_NAME_TAKEN' }, 'application/problem+json'),
    );

    expect(await api.create('Salón grande')).toEqual({
      ok: false,
      status: 409,
      code: 'ROOM_NAME_TAKEN',
    });
  });

  it('renames a room with PUT /api/rooms/{roomId}', async () => {
    const renamed = { ...room, name: 'Salón pequeño' };
    const { api, request } = apiWith(json(200, renamed));

    const result = await api.rename(room.id, 'Salón pequeño');

    expect(result).toEqual({ ok: true, room: renamed });
    expect(request().method).toBe('PUT');
    expect(request().url).toBe(`http://localhost/api/rooms/${room.id}`);
    expect(await request().json()).toEqual({ name: 'Salón pequeño' });
  });

  it('reports a rename of a room that is already deactivated', async () => {
    const { api } = apiWith(
      json(409, { code: 'ROOM_INACTIVE' }, 'application/problem+json'),
    );

    expect(await api.rename(room.id, 'Otro')).toEqual({
      ok: false,
      status: 409,
      code: 'ROOM_INACTIVE',
    });
  });

  it('deactivates a room with POST /api/rooms/{roomId}/deactivation', async () => {
    const { api, request } = apiWith(new Response(null, { status: 204 }));

    const result = await api.deactivate(room.id);

    expect(result).toEqual({ ok: true });
    expect(request().method).toBe('POST');
    expect(request().url).toBe(
      `http://localhost/api/rooms/${room.id}/deactivation`,
    );
  });

  it('reports how many active bookings keep a room from being deactivated', async () => {
    const { api } = apiWith(
      json(
        409,
        { code: 'ROOM_HAS_ACTIVE_BOOKINGS', activeBookings: 3 },
        'application/problem+json',
      ),
    );

    expect(await api.deactivate(room.id)).toEqual({
      ok: false,
      status: 409,
      code: 'ROOM_HAS_ACTIVE_BOOKINGS',
      activeBookings: 3,
    });
  });

  it('reports a refused deactivation', async () => {
    const { api } = apiWith(
      json(403, { code: 'FORBIDDEN' }, 'application/problem+json'),
    );

    expect(await api.deactivate(room.id)).toEqual({
      ok: false,
      status: 403,
      code: 'FORBIDDEN',
    });
  });

  describe('occupancy', () => {
    const occupancy = {
      roomId: room.id,
      date: '2030-05-11',
      occupied: [
        {
          startTime: '18:00',
          endTime: '20:00',
          status: 'PENDING',
          ownCommunity: true,
        },
      ],
    };

    it('reads GET /api/rooms/{roomId}/occupancy with the date', async () => {
      const { api, request } = apiWith(json(200, occupancy));

      const result = await api.occupancy(room.id, '2030-05-11');

      expect(result).toEqual({ ok: true, occupancy });
      expect(request().method).toBe('GET');
      expect(request().url).toBe(
        `http://localhost/api/rooms/${room.id}/occupancy?date=2030-05-11`,
      );
    });

    it('sends the community when one is given', async () => {
      const communityId = '6f1c1f1e-0000-4000-8000-000000000011';
      const { api, request } = apiWith(json(200, occupancy));

      await api.occupancy(room.id, '2030-05-11', communityId);

      expect(new URL(request().url).searchParams.get('communityId')).toBe(
        communityId,
      );
    });

    it('reports the field errors of a rejected query', async () => {
      const { api } = apiWith(
        json(
          422,
          {
            code: 'VALIDATION_FAILED',
            errors: [{ field: 'communityId', code: 'COMMUNITY_REQUIRED' }],
          },
          'application/problem+json',
        ),
      );

      expect(await api.occupancy(room.id, '2030-05-11')).toEqual({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'communityId', code: 'COMMUNITY_REQUIRED' }],
      });
    });

    it('reports a room that no longer exists', async () => {
      const { api } = apiWith(
        json(404, { code: 'NOT_FOUND' }, 'application/problem+json'),
      );

      expect(await api.occupancy(room.id, '2030-05-11')).toEqual({
        ok: false,
        status: 404,
        code: 'NOT_FOUND',
      });
    });
  });
});
