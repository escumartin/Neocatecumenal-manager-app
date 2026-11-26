import { createApiClient } from '../client';
import { createServiceApi } from '../service-api';

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
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
    api: createServiceApi(client),
    request: () => fetch.mock.calls[0][0] as Request,
  };
}

describe('service api', () => {
  it('reads the compatibility from GET /api/compatibility without a session', async () => {
    const compatibility = { minimumAppVersion: '1.2.0', serviceRevision: '86a5aa6' };
    const { api, request } = apiWith(json(200, compatibility));

    const result = await api.getCompatibility();

    expect(result).toEqual({ ok: true, compatibility });
    expect(request().method).toBe('GET');
    expect(request().url).toBe('http://localhost/api/compatibility');
    expect(request().headers.get('Authorization')).toBeNull();
  });

  it('reports the status when the service answers with an error', async () => {
    const { api } = apiWith(json(503, { status: 503 }));

    expect(await api.getCompatibility()).toEqual({ ok: false, status: 503 });
  });
});
