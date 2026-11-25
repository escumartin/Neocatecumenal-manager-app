import { createApiClient } from '../client';
import { createMembersApi } from '../members-api';

const ID = '6f1c1f1e-0000-4000-8000-000000000041';
const COMMUNITY = '6f1c1f1e-0000-4000-8000-000000000031';

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
  return { api: createMembersApi(client), fetch };
}

function sentRequest(fetch: jest.Mock) {
  return fetch.mock.calls[0][0] as Request;
}

const member = { id: ID, communityId: COMMUNITY, givenName: 'Ana', isResponsible: false };
const contact = { mobile: { shared: true, value: '600000001' }, landline: { shared: false }, email: { shared: false } };

describe('members api', () => {
  it('lists the members of the own community without a query', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, [member]));

    expect(await api.list()).toEqual({ ok: true, members: [member] });
    expect(sentRequest(fetch).url).toBe('http://localhost/api/members');
  });

  it('lists the members of the community an administrator asks for', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, []));

    await api.list(COMMUNITY);

    expect(sentRequest(fetch).url).toBe(`http://localhost/api/members?communityId=${COMMUNITY}`);
  });

  it('reports the status and code of a refused list', async () => {
    const { api } = apiWith(jsonResponse(403, { code: 'FORBIDDEN' }));

    expect(await api.list()).toEqual({ ok: false, status: 403, code: 'FORBIDDEN' });
  });

  it('gets one member', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, member));

    expect(await api.get(ID)).toEqual({ ok: true, member });
    expect(sentRequest(fetch).url).toBe(`http://localhost/api/members/${ID}`);
  });

  it('reports a missing member', async () => {
    const { api } = apiWith(jsonResponse(404, { code: 'NOT_FOUND' }));

    expect(await api.get(ID)).toEqual({ ok: false, status: 404, code: 'NOT_FOUND' });
  });

  it('creates a member with POST /api/members', async () => {
    const { api, fetch } = apiWith(jsonResponse(201, member));
    const input = {
      communityId: COMMUNITY,
      givenName: 'Ana',
      contact,
      onBehalfConsent: { channel: 'VERBAL' as const, noticeGiven: true },
    };

    expect(await api.create(input)).toEqual({ ok: true, member });
    expect(sentRequest(fetch).method).toBe('POST');
    expect(await sentRequest(fetch).json()).toEqual(input);
  });

  it('reports the field errors of a refused creation', async () => {
    const errors = [{ field: 'mobile', code: 'PHONE_INVALID' }];
    const { api } = apiWith(jsonResponse(422, { code: 'VALIDATION_FAILED', errors }));

    expect(await api.create({ communityId: COMMUNITY, givenName: 'Ana' })).toEqual({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: errors,
    });
  });

  it('renames a member with PUT', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, member));

    expect(await api.rename(ID, { givenName: 'Ana', surnames: 'Pérez' })).toEqual({ ok: true, member });
    expect(sentRequest(fetch).method).toBe('PUT');
    expect(sentRequest(fetch).url).toBe(`http://localhost/api/members/${ID}`);
    expect(await sentRequest(fetch).json()).toEqual({ givenName: 'Ana', surnames: 'Pérez' });
  });

  it('changes the contact of a member with PUT .../contact', async () => {
    const { api, fetch } = apiWith(jsonResponse(200, member));

    expect(await api.changeContact(ID, contact)).toEqual({ ok: true, member });
    expect(sentRequest(fetch).method).toBe('PUT');
    expect(sentRequest(fetch).url).toBe(`http://localhost/api/members/${ID}/contact`);
    expect(await sentRequest(fetch).json()).toEqual(contact);
  });
});
