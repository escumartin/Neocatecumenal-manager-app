import { createApiClient } from '../client';
import { createUsersApi } from '../users-api';

const communityId = '6f1c1f1e-0000-4000-8000-000000000011';
const user = {
  id: '6f1c1f1e-0000-4000-8000-000000000021',
  accessId: 482913,
  name: 'Test User',
  role: 'REGISTERED' as const,
  communityId,
  passwordChangePending: true,
};

function json(status: number, body: unknown, type = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } });
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
  return { api: createUsersApi(client), request: () => fetch.mock.calls[0][0] as Request };
}

describe('users api', () => {
  it('lists the users of a community from GET /api/users', async () => {
    const { api, request } = apiWith(json(200, { items: [user], page: 0, size: 50, totalItems: 1 }));

    const result = await api.list(communityId);

    expect(result).toEqual({ ok: true, users: [user] });
    expect(request().url).toBe(`http://localhost/api/users?communityId=${communityId}`);
  });

  it('lists without a community filter when none is given', async () => {
    const { api, request } = apiWith(json(200, { items: [], page: 0, size: 50, totalItems: 0 }));

    await api.list();

    expect(request().url).toBe('http://localhost/api/users');
  });

  it('creates a user with POST /api/users and returns the initial password', async () => {
    const { api, request } = apiWith(json(201, { ...user, initialPassword: 'abcd234567' }));

    const result = await api.create({ name: 'Test User', role: 'REGISTERED', communityId });

    expect(result).toEqual({ ok: true, user: { ...user, initialPassword: 'abcd234567' } });
    expect(request().method).toBe('POST');
    expect(await request().json()).toEqual({ name: 'Test User', role: 'REGISTERED', communityId });
  });

  it('reports the field errors of a rejected creation', async () => {
    const { api } = apiWith(
      json(422, { code: 'VALIDATION_FAILED', errors: [{ field: 'name', code: 'REQUIRED' }] }, 'application/problem+json'),
    );

    const result = await api.create({ name: '', role: 'REGISTERED', communityId });

    expect(result).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'name', code: 'REQUIRED' }],
    });
  });

  it('updates a user with PUT /api/users/{id}', async () => {
    const { api, request } = apiWith(json(200, user));

    const result = await api.update(user.id, { name: 'Test User', role: 'REGISTERED', communityId });

    expect(result).toEqual({ ok: true, user });
    expect(request().method).toBe('PUT');
    expect(request().url).toBe(`http://localhost/api/users/${user.id}`);
  });

  it('deletes a user with DELETE /api/users/{id}', async () => {
    const { api, request } = apiWith(new Response(null, { status: 204 }));

    expect(await api.remove(user.id)).toEqual({ ok: true });
    expect(request().method).toBe('DELETE');
  });

  it('reports the problem code when a deletion breaks an invariant', async () => {
    const { api } = apiWith(json(409, { code: 'LAST_MODERATOR' }, 'application/problem+json'));

    expect(await api.remove(user.id)).toEqual({ ok: false, status: 409, code: 'LAST_MODERATOR' });
  });

  it('gets one user with GET /api/users/{id}', async () => {
    const { api } = apiWith(json(200, user));

    expect(await api.get(user.id)).toEqual({ ok: true, user });
  });

  it('reports the status when a user cannot be read', async () => {
    const { api } = apiWith(json(404, { code: 'NOT_FOUND' }, 'application/problem+json'));

    expect(await api.get(user.id)).toEqual({ ok: false, status: 404, code: 'NOT_FOUND' });
  });

  it('resets the password with POST /api/users/{id}/password-reset', async () => {
    const { api, request } = apiWith(json(200, { initialPassword: 'wxyz345678' }));

    const result = await api.resetPassword(user.id);

    expect(result).toEqual({ ok: true, initialPassword: 'wxyz345678' });
    expect(request().method).toBe('POST');
    expect(request().url).toBe(`http://localhost/api/users/${user.id}/password-reset`);
  });

  it('reports a forbidden password reset', async () => {
    const { api } = apiWith(json(403, { code: 'SELF_ACTION_FORBIDDEN' }));

    const result = await api.resetPassword(user.id);

    expect(result).toEqual({ ok: false, status: 403, code: 'SELF_ACTION_FORBIDDEN' });
  });
});
