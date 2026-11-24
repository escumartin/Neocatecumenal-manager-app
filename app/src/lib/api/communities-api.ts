import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type Community = components['schemas']['Community'];

export type ListCommunitiesResult = { ok: true; communities: Community[] } | { ok: false; status: number };

export type CommunitiesApi = {
  list: () => Promise<ListCommunitiesResult>;
};

export function createCommunitiesApi(client: ApiClient): CommunitiesApi {
  return {
    async list() {
      const { data, response } = await client.GET('/api/communities');
      return data ? { ok: true, communities: data } : { ok: false, status: response.status };
    },
  };
}
