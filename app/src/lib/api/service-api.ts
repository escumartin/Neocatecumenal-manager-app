import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type Compatibility = components['schemas']['Compatibility'];

export type GetCompatibilityResult =
  { ok: true; compatibility: Compatibility } | { ok: false; status: number };

export type ServiceApi = {
  getCompatibility: () => Promise<GetCompatibilityResult>;
};

export function createServiceApi(client: ApiClient): ServiceApi {
  return {
    async getCompatibility() {
      const { data, response } = await client.GET('/api/compatibility');
      return data
        ? { ok: true, compatibility: data }
        : { ok: false, status: response.status };
    },
  };
}
