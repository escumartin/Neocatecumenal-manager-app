import { createApiClient } from '../client';

function problemResponse(status: number, code: string) {
  return new Response(JSON.stringify({ type: 'about:blank', title: 'x', status, code }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function setup(response: Response, token: string | null = 'token-1') {
  const fetchMock = jest.fn().mockResolvedValue(response);
  const onUnauthorized = jest.fn();
  const onPendingStep = jest.fn();
  const client = createApiClient({
    baseUrl: 'http://api.test',
    readToken: async () => token,
    onUnauthorized,
    onPendingStep,
    fetch: fetchMock,
  });
  return { client, fetchMock, onUnauthorized, onPendingStep };
}

function sentRequest(fetchMock: jest.Mock): Request {
  return fetchMock.mock.calls[0][0] as Request;
}

describe('api client', () => {
  it('accepts responses from a native fetch that are not instances of the global Response', async () => {
    const body = { userId: 'user-1' };
    const nativeResponse = {
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => body,
      text: async () => JSON.stringify(body),
      clone() {
        return this;
      },
    } as unknown as Response;
    const { client } = setup(nativeResponse);

    const { data } = await client.GET('/api/me');

    expect(data).toEqual(body);
  });

  it('adds the bearer token when a session token exists', async () => {
    const { client, fetchMock } = setup(new Response(null, { status: 204 }));

    await client.DELETE('/api/sessions/current');

    expect(sentRequest(fetchMock).headers.get('Authorization')).toBe('Bearer token-1');
  });

  it('sends no authorization header without a token', async () => {
    const { client, fetchMock } = setup(new Response(null, { status: 204 }), null);

    await client.DELETE('/api/sessions/current');

    expect(sentRequest(fetchMock).headers.get('Authorization')).toBeNull();
  });

  it('reports an unauthenticated response so the user returns to login', async () => {
    const { client, onUnauthorized } = setup(problemResponse(401, 'UNAUTHENTICATED'));

    await client.GET('/api/me');

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does not treat invalid credentials as an expired session', async () => {
    const { client, onUnauthorized } = setup(problemResponse(401, 'INVALID_CREDENTIALS'));

    await client.POST('/api/sessions', { body: { accessId: 482913, password: 'x', tokenDelivery: 'BODY' } });

    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it.each(['PRIVACY_NOTICE_PENDING', 'PRIVACY_NOTICE_OUTDATED', 'PASSWORD_CHANGE_REQUIRED'])(
    'reports the pending step on a 403 with %s',
    async (code) => {
      const { client, onPendingStep, onUnauthorized } = setup(problemResponse(403, code));

      await client.GET('/api/me');

      expect(onPendingStep).toHaveBeenCalledWith(code);
      expect(onUnauthorized).not.toHaveBeenCalled();
    },
  );

  it('ignores a plain 403 forbidden', async () => {
    const { client, onPendingStep } = setup(problemResponse(403, 'FORBIDDEN'));

    await client.GET('/api/me');

    expect(onPendingStep).not.toHaveBeenCalled();
  });

  it('still returns the problem body to the caller after inspecting it', async () => {
    const { client } = setup(problemResponse(403, 'PASSWORD_CHANGE_REQUIRED'));

    const { error } = await client.GET('/api/me');

    expect(error).toMatchObject({ code: 'PASSWORD_CHANGE_REQUIRED' });
  });
});
