import { createApiClient } from '../client';
import { createCommunitiesApi } from '../communities-api';

function apiWith(response: Response) {
  const fetch = jest.fn().mockResolvedValue(response);
  const client = createApiClient({
    baseUrl: 'http://localhost',
    readToken: async () => null,
    onUnauthorized: jest.fn(),
    onPendingStep: jest.fn(),
    fetch,
  });
  return { api: createCommunitiesApi(client), fetch };
}

describe('communities api', () => {
  it('returns the communities from GET /api/communities', async () => {
    const body = [{ id: '6f1c1f1e-0000-4000-8000-000000000011', number: 1 }];
    const { api, fetch } = apiWith(
      new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );

    const result = await api.list();

    expect(result).toEqual({ ok: true, communities: body });
    expect((fetch.mock.calls[0][0] as Request).url).toBe('http://localhost/api/communities');
  });

  it('reports the status when the request is refused', async () => {
    const { api } = apiWith(new Response('{}', { status: 403, headers: { 'Content-Type': 'application/json' } }));

    expect(await api.list()).toEqual({ ok: false, status: 403 });
  });
});
