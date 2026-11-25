import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type Community = components['schemas']['Community'];
export type CommunityDetail = components['schemas']['CommunityDetail'];
export type CommunityInput = components['schemas']['CommunityInput'];
export type FieldError = components['schemas']['ValidationProblem']['errors'][number];

export type CommunitiesFailure = { ok: false; status: number; code?: string; fieldErrors?: FieldError[] };

export type ListCommunitiesOptions = { includeDeactivated?: boolean };

export type ListCommunitiesResult = { ok: true; communities: Community[] } | CommunitiesFailure;
export type CommunityResult = { ok: true; community: CommunityDetail } | CommunitiesFailure;
export type CommunityOutcome = { ok: true } | CommunitiesFailure;

export type CommunitiesApi = {
  list: (options?: ListCommunitiesOptions) => Promise<ListCommunitiesResult>;
};

export type CommunityManagementApi = {
  get: (communityId: string) => Promise<CommunityResult>;
  create: (input: CommunityInput) => Promise<CommunityResult>;
  update: (communityId: string, input: CommunityInput) => Promise<CommunityResult>;
  remove: (communityId: string) => Promise<CommunityOutcome>;
  deactivate: (communityId: string, confirmed: boolean) => Promise<CommunityOutcome>;
  reactivate: (communityId: string) => Promise<CommunityOutcome>;
};

function failure(error: unknown, response: Response): CommunitiesFailure {
  const problem = error as { code?: string; errors?: FieldError[] } | undefined;
  return {
    ok: false,
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.errors ? { fieldErrors: problem.errors } : {}),
  };
}

export function createCommunitiesApi(client: ApiClient): CommunitiesApi & CommunityManagementApi {
  return {
    async list(options) {
      const { data, error, response } = await client.GET('/api/communities', {
        params: { query: options?.includeDeactivated ? { includeDeactivated: true } : {} },
      });
      return data ? { ok: true, communities: data } : failure(error, response);
    },
    async get(communityId) {
      const { data, error, response } = await client.GET('/api/communities/{communityId}', {
        params: { path: { communityId } },
      });
      return data ? { ok: true, community: data } : failure(error, response);
    },
    async create(input) {
      const { data, error, response } = await client.POST('/api/communities', { body: input });
      return data ? { ok: true, community: data } : failure(error, response);
    },
    async update(communityId, input) {
      const { data, error, response } = await client.PUT('/api/communities/{communityId}', {
        params: { path: { communityId } },
        body: input,
      });
      return data ? { ok: true, community: data } : failure(error, response);
    },
    async remove(communityId) {
      const { error, response } = await client.DELETE('/api/communities/{communityId}', {
        params: { path: { communityId } },
      });
      return response.ok ? { ok: true } : failure(error, response);
    },
    async deactivate(communityId, confirmed) {
      const { error, response } = await client.POST('/api/communities/{communityId}/deactivation', {
        params: { path: { communityId } },
        body: { confirmed },
      });
      return response.ok ? { ok: true } : failure(error, response);
    },
    async reactivate(communityId) {
      const { error, response } = await client.POST('/api/communities/{communityId}/reactivation', {
        params: { path: { communityId } },
      });
      return response.ok ? { ok: true } : failure(error, response);
    },
  };
}
