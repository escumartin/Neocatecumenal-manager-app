import { createApiClient } from '../client';
import { createSessionApi } from '../session-api';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
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
  return { api: createSessionApi(client, 'BODY'), request: () => fetch.mock.calls[0][0] as Request };
}

describe('session api own password and privacy notice', () => {
  it('changes the own password with PUT /api/me/password', async () => {
    const { api, request } = apiWith(new Response(null, { status: 204 }));

    const result = await api.changePassword('current-secret', 'new-secret-1');

    expect(result).toEqual({ ok: true });
    expect(request().method).toBe('PUT');
    expect(request().url).toBe('http://localhost/api/me/password');
    expect(await request().json()).toEqual({ currentPassword: 'current-secret', newPassword: 'new-secret-1' });
  });

  it('reports the field errors of a rejected password change', async () => {
    const errors = [{ field: 'newPassword', code: 'PASSWORD_TOO_COMMON' }];
    const { api } = apiWith(json(422, { code: 'VALIDATION_FAILED', errors }));

    const result = await api.changePassword('current-secret', 'password');

    expect(result).toEqual({ ok: false, status: 422, code: 'VALIDATION_FAILED', fieldErrors: errors });
  });

  it('accepts the privacy notice with POST /api/me/privacy-notice-acceptance', async () => {
    const { api, request } = apiWith(new Response(null, { status: 204 }));

    const result = await api.acceptPrivacyNotice('2');

    expect(result).toEqual({ ok: true });
    expect(request().url).toBe('http://localhost/api/me/privacy-notice-acceptance');
    expect(await request().json()).toEqual({ version: '2' });
  });

  it('reports an outdated privacy notice version', async () => {
    const { api } = apiWith(json(409, { code: 'PRIVACY_NOTICE_OUTDATED' }));

    const result = await api.acceptPrivacyNotice('1');

    expect(result).toEqual({ ok: false, status: 409, code: 'PRIVACY_NOTICE_OUTDATED' });
  });

  it('rejects the privacy notice with POST /api/me/privacy-notice-rejection', async () => {
    const { api, request } = apiWith(new Response(null, { status: 204 }));

    const result = await api.rejectPrivacyNotice();

    expect(result).toEqual({ ok: true });
    expect(request().url).toBe('http://localhost/api/me/privacy-notice-rejection');
    expect(request().method).toBe('POST');
  });
});

describe('session api log in', () => {
  const me = { id: '6f1c1f1e-0000-4000-8000-000000000001', name: 'Ana', accessId: 1, role: 'REGISTERED', pendingSteps: [] };

  it('does not report a token when the server sets a cookie', async () => {
    const { api } = apiWith(json(200, { me }));

    const result = await api.logIn(1, 'secret-1');

    expect(result).toStrictEqual({ ok: true, me });
  });

  it('reports the token when the server returns it in the body', async () => {
    const { api } = apiWith(json(200, { token: 'abc', me }));

    const result = await api.logIn(1, 'secret-1');

    expect(result).toStrictEqual({ ok: true, token: 'abc', me });
  });

  it('reports a login refused because of the origin', async () => {
    const { api } = apiWith(json(403, { code: 'FORBIDDEN' }));

    const result = await api.logIn(1, 'secret-1');

    expect(result).toStrictEqual({ ok: false, code: 'FORBIDDEN' });
  });
});
