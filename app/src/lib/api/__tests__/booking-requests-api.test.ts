import { createApiClient } from '../client';
import { createBookingRequestsApi } from '../booking-requests-api';

const request = {
  id: '6f1c1f1e-0000-4000-8000-000000000041',
  roomId: '6f1c1f1e-0000-4000-8000-000000000031',
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  date: '2030-05-11',
  startTime: '18:00',
  endTime: '20:00',
  reason: 'Catequesis',
  status: 'PENDING',
  occurrenceCount: 1,
  createdAt: '2030-05-10T08:00:00Z',
};

const input = {
  roomId: request.roomId,
  date: request.date,
  startTime: request.startTime,
  endTime: request.endTime,
  reason: request.reason,
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
    api: createBookingRequestsApi(client),
    sent: () => fetch.mock.calls[0][0] as Request,
  };
}

describe('booking requests api', () => {
  it('creates a request with POST /api/booking-requests', async () => {
    const { api, sent } = apiWith(json(201, request));

    const result = await api.create({
      ...input,
      communityId: request.communityId,
    });

    expect(result).toEqual({ ok: true, request });
    expect(sent().method).toBe('POST');
    expect(sent().url).toBe('http://localhost/api/booking-requests');
    expect(await sent().json()).toEqual({
      ...input,
      communityId: request.communityId,
    });
  });

  it('sends the weekly recurrence of a series and reads its occurrence count', async () => {
    const series = {
      ...request,
      occurrenceCount: 8,
      recurrence: { frequency: 'WEEKLY', until: '2030-06-29' },
    };
    const { api, sent } = apiWith(json(201, series));

    const result = await api.create({
      ...input,
      recurrence: { frequency: 'WEEKLY', until: '2030-06-29' },
    });

    expect(result).toEqual({ ok: true, request: series });
    expect(await sent().json()).toEqual({
      ...input,
      recurrence: { frequency: 'WEEKLY', until: '2030-06-29' },
    });
  });

  it('reports the end date error of a rejected series', async () => {
    const { api } = apiWith(
      json(
        422,
        {
          code: 'VALIDATION_FAILED',
          errors: [{ field: 'recurrence.until', code: 'RECURRENCE_TOO_LONG' }],
        },
        'application/problem+json',
      ),
    );

    const result = await api.create({
      ...input,
      recurrence: { frequency: 'WEEKLY', until: '2032-06-29' },
    });

    expect(result).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'recurrence.until', code: 'RECURRENCE_TOO_LONG' }],
    });
  });

  it('reports every conflicting date of a refused series', async () => {
    const conflicts = [
      { date: '2030-05-25', startTime: '18:00', endTime: '20:00' },
      { date: '2030-06-08', startTime: '18:00', endTime: '20:00' },
    ];
    const { api } = apiWith(
      json(
        409,
        { code: 'ROOM_OCCUPIED', conflicts },
        'application/problem+json',
      ),
    );

    const result = await api.create({
      ...input,
      recurrence: { frequency: 'WEEKLY', until: '2030-06-29' },
    });

    expect(result).toMatchObject({ ok: false, status: 409, conflicts });
  });

  it('reports the field errors of a rejected creation', async () => {
    const { api } = apiWith(
      json(
        422,
        {
          code: 'VALIDATION_FAILED',
          errors: [{ field: 'endTime', code: 'END_NOT_AFTER_START' }],
        },
        'application/problem+json',
      ),
    );

    expect(await api.create(input)).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'endTime', code: 'END_NOT_AFTER_START' }],
    });
  });

  it('reports a room that is no longer active', async () => {
    const { api } = apiWith(
      json(409, { code: 'ROOM_INACTIVE' }, 'application/problem+json'),
    );

    expect(await api.create(input)).toEqual({
      ok: false,
      status: 409,
      code: 'ROOM_INACTIVE',
    });
  });

  it('reports the conflicts of an occupied room', async () => {
    const conflicts = [
      { date: '2030-05-11', startTime: '18:00', endTime: '20:00' },
    ];
    const { api } = apiWith(
      json(
        409,
        { code: 'ROOM_OCCUPIED', conflicts },
        'application/problem+json',
      ),
    );

    expect(await api.create(input)).toEqual({
      ok: false,
      status: 409,
      code: 'ROOM_OCCUPIED',
      conflicts,
    });
  });

  it('reports an occupied room without conflicts after a race', async () => {
    const { api } = apiWith(
      json(
        409,
        { code: 'ROOM_OCCUPIED', conflicts: [] },
        'application/problem+json',
      ),
    );

    expect(await api.create(input)).toEqual({
      ok: false,
      status: 409,
      code: 'ROOM_OCCUPIED',
      conflicts: [],
    });
  });

  it('reports a refused creation', async () => {
    const { api } = apiWith(
      json(403, { code: 'FORBIDDEN' }, 'application/problem+json'),
    );

    expect(await api.create(input)).toEqual({
      ok: false,
      status: 403,
      code: 'FORBIDDEN',
    });
  });

  it('lists the requests with GET /api/booking-requests and the paging parameters', async () => {
    const page = { items: [request], page: 0, size: 100, totalItems: 1 };
    const { api, sent } = apiWith(json(200, page));

    const result = await api.list({ size: 100 });

    expect(result).toEqual({ ok: true, page });
    expect(sent().method).toBe('GET');
    expect(sent().url).toBe('http://localhost/api/booking-requests?size=100');
  });

  it('filters the list by status and community', async () => {
    const { api, sent } = apiWith(
      json(200, { items: [], page: 0, size: 20, totalItems: 0 }),
    );

    await api.list({
      status: ['PENDING', 'APPROVED'],
      communityId: request.communityId,
    });

    const url = new URL(sent().url);
    expect(url.searchParams.getAll('status')).toEqual(['PENDING', 'APPROVED']);
    expect(url.searchParams.get('communityId')).toBe(request.communityId);
  });

  it('reports the status when the list is refused', async () => {
    const { api } = apiWith(json(403, {}, 'application/problem+json'));

    expect(await api.list()).toEqual({ ok: false, status: 403 });
  });

  it('corrects the reason with PUT /api/booking-requests/{id}/reason', async () => {
    const corrected = { ...request, reason: 'Catequesis juvenil' };
    const { api, sent } = apiWith(json(200, corrected));

    const result = await api.rectifyReason(request.id, 'Catequesis juvenil');

    expect(result).toEqual({ ok: true, request: corrected });
    expect(sent().method).toBe('PUT');
    expect(sent().url).toBe(
      `http://localhost/api/booking-requests/${request.id}/reason`,
    );
    expect(await sent().json()).toEqual({ reason: 'Catequesis juvenil' });
  });

  it('reports a correction of a request that is already resolved', async () => {
    const { api } = apiWith(
      json(
        409,
        { code: 'BOOKING_REQUEST_ALREADY_RESOLVED' },
        'application/problem+json',
      ),
    );

    expect(await api.rectifyReason(request.id, 'Otro')).toEqual({
      ok: false,
      status: 409,
      code: 'BOOKING_REQUEST_ALREADY_RESOLVED',
    });
  });

  it('approves with POST /api/booking-requests/{id}/approval', async () => {
    const approved = { ...request, status: 'APPROVED' };
    const { api, sent } = apiWith(json(200, approved));

    const result = await api.approve(request.id);

    expect(result).toEqual({ ok: true, request: approved });
    expect(sent().method).toBe('POST');
    expect(sent().url).toBe(
      `http://localhost/api/booking-requests/${request.id}/approval`,
    );
  });

  it('reports an approval of a request that started or was resolved', async () => {
    const started = apiWith(
      json(422, {
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'startTime', code: 'START_NOT_IN_FUTURE' }],
      }),
    );
    const resolved = apiWith(
      json(
        409,
        { code: 'BOOKING_REQUEST_ALREADY_RESOLVED' },
        'application/problem+json',
      ),
    );

    expect(await started.api.approve(request.id)).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'startTime', code: 'START_NOT_IN_FUTURE' }],
    });
    expect(await resolved.api.approve(request.id)).toEqual({
      ok: false,
      status: 409,
      code: 'BOOKING_REQUEST_ALREADY_RESOLVED',
    });
  });

  it('rejects with the reason through POST /api/booking-requests/{id}/rejection', async () => {
    const rejected = {
      ...request,
      status: 'REJECTED',
      rejectionReason: 'Salón en obras',
    };
    const { api, sent } = apiWith(json(200, rejected));

    const result = await api.reject(request.id, 'Salón en obras');

    expect(result).toEqual({ ok: true, request: rejected });
    expect(sent().method).toBe('POST');
    expect(sent().url).toBe(
      `http://localhost/api/booking-requests/${request.id}/rejection`,
    );
    expect(await sent().json()).toEqual({ reason: 'Salón en obras' });
  });

  it('rejects without a reason by sending an empty object', async () => {
    const { api, sent } = apiWith(
      json(200, { ...request, status: 'REJECTED' }),
    );

    await api.reject(request.id);

    expect(await sent().json()).toEqual({});
  });

  it('reports a refused or conflicting rejection', async () => {
    const forbidden = apiWith(
      json(403, { code: 'FORBIDDEN' }, 'application/problem+json'),
    );
    const resolved = apiWith(
      json(
        409,
        { code: 'BOOKING_REQUEST_ALREADY_RESOLVED' },
        'application/problem+json',
      ),
    );

    expect(await forbidden.api.reject(request.id)).toEqual({
      ok: false,
      status: 403,
      code: 'FORBIDDEN',
    });
    expect(await resolved.api.reject(request.id)).toEqual({
      ok: false,
      status: 409,
      code: 'BOOKING_REQUEST_ALREADY_RESOLVED',
    });
  });

  it('cancels a request with POST /api/booking-requests/{id}/cancellation', async () => {
    const cancelled = { ...request, status: 'CANCELLED' };
    const { api, sent } = apiWith(json(200, cancelled));

    const result = await api.cancel(request.id);

    expect(result).toEqual({ ok: true, request: cancelled });
    expect(sent().method).toBe('POST');
    expect(sent().url).toBe(
      `http://localhost/api/booking-requests/${request.id}/cancellation`,
    );
  });

  it.each([
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [409, 'BOOKING_ALREADY_STARTED'],
    [409, 'BOOKING_REQUEST_NOT_ACTIVE'],
  ])('reports a refused cancellation %s %s', async (status, code) => {
    const { api } = apiWith(json(status, { code }, 'application/problem+json'));

    expect(await api.cancel(request.id)).toEqual({ ok: false, status, code });
  });

  it('cancels one occurrence with POST .../bookings/{bookingId}/cancellation and returns the detail', async () => {
    const bookingId = '6f1c1f1e-0000-4000-8000-000000000051';
    const detail = { ...request, bookings: [], history: [] };
    const { api, sent } = apiWith(json(200, detail));

    const result = await api.cancelBooking(request.id, bookingId);

    expect(result).toEqual({ ok: true, detail });
    expect(sent().method).toBe('POST');
    expect(sent().url).toBe(
      `http://localhost/api/booking-requests/${request.id}/bookings/${bookingId}/cancellation`,
    );
  });

  it('reports a refused occurrence cancellation', async () => {
    const { api } = apiWith(
      json(
        409,
        { code: 'BOOKING_ALREADY_STARTED' },
        'application/problem+json',
      ),
    );

    expect(await api.cancelBooking(request.id, 'x')).toEqual({
      ok: false,
      status: 409,
      code: 'BOOKING_ALREADY_STARTED',
    });
  });

  it('reads the detail of a request with GET /api/booking-requests/{id}', async () => {
    const detail = { ...request, bookings: [], history: [] };
    const { api, sent } = apiWith(json(200, detail));

    expect(await api.get(request.id)).toEqual({ ok: true, detail });
    expect(sent().method).toBe('GET');
    expect(sent().url).toBe(
      `http://localhost/api/booking-requests/${request.id}`,
    );
  });

  it('reports a detail that cannot be read', async () => {
    const { api } = apiWith(
      json(404, { code: 'NOT_FOUND' }, 'application/problem+json'),
    );

    expect(await api.get(request.id)).toEqual({
      ok: false,
      status: 404,
      code: 'NOT_FOUND',
    });
  });
});
