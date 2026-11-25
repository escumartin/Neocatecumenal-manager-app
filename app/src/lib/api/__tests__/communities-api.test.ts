import { createApiClient } from '../client';
import { createCommunitiesApi } from '../communities-api';

const ID = '6f1c1f1e-0000-4000-8000-000000000011';

function jsonResponse(status: number, body: unknown) {
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
  return { api: createCommunitiesApi(client), fetch };
}

function sentRequest(fetch: jest.Mock) {
  return fetch.mock.calls[0][0] as Request;
}

const detail = {
  id: ID,
  number: 12,
  name: 'San Pedro',
  status: 'ACTIVE',
  hasResponsible: false,
  responsibleIsModerator: false,
};

describe('communities api', () => {
  it('returns the communities from GET /api/communities', async () => {
    const body = [{ id: ID, number: 1 }];
    const { api, fetch } = apiWith(jsonResponse(200, body));

    const result = await api.list();

    expect(result).toEqual({ ok: true, communities: body });
    expect(sentRequest(fetch).url).toBe('http://localhost/api/communities');
  });

  it('asks for the deactivated communities too when includeDeactivated is set', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, []));

    await api.list({ includeDeactivated: true });

    expect(sentRequest(fetch).url).toBe('http://localhost/api/communities?includeDeactivated=true');
  });

  it('does not send includeDeactivated when it is false', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, []));

    await api.list({ includeDeactivated: false });

    expect(sentRequest(fetch).url).toBe('http://localhost/api/communities');
  });

  it('reports the status when the request is refused', async () => {
    const { api } = apiWith(jsonResponse(403, {}));

    expect(await api.list()).toEqual({ ok: false, status: 403 });
  });

  it('gets one community file', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, detail));

    const result = await api.get(ID);

    expect(result).toEqual({ ok: true, community: detail });
    expect(sentRequest(fetch).method).toBe('GET');
    expect(sentRequest(fetch).url).toBe(`http://localhost/api/communities/${ID}`);
  });

  it('reports a missing community file with its status and code', async () => {
    const { api } = apiWith(jsonResponse(404, { code: 'NOT_FOUND' }));

    expect(await api.get(ID)).toEqual({ ok: false, status: 404, code: 'NOT_FOUND' });
  });

  it('creates a community with POST /api/communities', async () => {
    const { api, fetch } = apiWith(jsonResponse(201, detail));

    const result = await api.create({ number: 12, name: 'San Pedro' });

    expect(result).toEqual({ ok: true, community: detail });
    expect(sentRequest(fetch).method).toBe('POST');
    expect(await sentRequest(fetch).json()).toEqual({ number: 12, name: 'San Pedro' });
  });

  it('reports the field errors of a refused creation', async () => {
    const errors = [{ field: 'number', code: 'COMMUNITY_NUMBER_TAKEN' }];
    const { api } = apiWith(jsonResponse(422, { code: 'VALIDATION_FAILED', errors }));

    expect(await api.create({ number: 12 })).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: errors,
    });
  });

  it('updates a community with PUT', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, detail));

    const result = await api.update(ID, { number: 12, name: 'San Pedro' });

    expect(result).toEqual({ ok: true, community: detail });
    expect(sentRequest(fetch).method).toBe('PUT');
    expect(sentRequest(fetch).url).toBe(`http://localhost/api/communities/${ID}`);
  });

  it('deletes a community with DELETE', async () => {
    const { api, fetch } = apiWith(new Response(null, { status: 204 }));

    expect(await api.remove(ID)).toEqual({ ok: true });
    expect(sentRequest(fetch).method).toBe('DELETE');
  });

  it('reports the rejection code of a refused deletion', async () => {
    const { api } = apiWith(jsonResponse(409, { code: 'COMMUNITY_HAS_RESPONSIBLE' }));

    expect(await api.remove(ID)).toEqual({ ok: false, status: 409, code: 'COMMUNITY_HAS_RESPONSIBLE' });
  });

  it.each([true, false])('deactivates a community sending confirmed %s', async (confirmed) => {
    const { api, fetch } = apiWith(new Response(null, { status: 204 }));

    expect(await api.deactivate(ID, confirmed)).toEqual({ ok: true });
    expect(sentRequest(fetch).method).toBe('POST');
    expect(sentRequest(fetch).url).toBe(`http://localhost/api/communities/${ID}/deactivation`);
    expect(await sentRequest(fetch).json()).toEqual({ confirmed });
  });

  it('reports the privileged users conflict of a deactivation', async () => {
    const { api } = apiWith(jsonResponse(409, { code: 'COMMUNITY_HAS_PRIVILEGED_USERS' }));

    expect(await api.deactivate(ID, false)).toEqual({
      ok: false,
      status: 409,
      code: 'COMMUNITY_HAS_PRIVILEGED_USERS',
    });
  });

  it('reactivates a community with POST', async () => {
    const { api, fetch } = apiWith(new Response(null, { status: 204 }));

    expect(await api.reactivate(ID)).toEqual({ ok: true });
    expect(sentRequest(fetch).url).toBe(`http://localhost/api/communities/${ID}/reactivation`);
  });
});
