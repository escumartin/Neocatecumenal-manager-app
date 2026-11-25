import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type Member = components['schemas']['Member'];
export type MemberInput = components['schemas']['MemberInput'];
export type MemberNameInput = components['schemas']['MemberNameInput'];
export type MemberContactInput = components['schemas']['MemberContactInput'];
export type FieldError = components['schemas']['ValidationProblem']['errors'][number];

export type MembersFailure = { ok: false; status: number; code?: string; fieldErrors?: FieldError[] };

export type ListMembersResult = { ok: true; members: Member[] } | MembersFailure;
export type MemberResult = { ok: true; member: Member } | MembersFailure;

export type MembersApi = {
  list: (communityId?: string) => Promise<ListMembersResult>;
};

export type MemberManagementApi = {
  get: (memberId: string) => Promise<MemberResult>;
  create: (input: MemberInput) => Promise<MemberResult>;
  rename: (memberId: string, input: MemberNameInput) => Promise<MemberResult>;
  changeContact: (memberId: string, input: MemberContactInput) => Promise<MemberResult>;
};

function failure(error: unknown, response: Response): MembersFailure {
  const problem = error as { code?: string; errors?: FieldError[] } | undefined;
  return {
    ok: false,
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.errors ? { fieldErrors: problem.errors } : {}),
  };
}

export function createMembersApi(client: ApiClient): MembersApi & MemberManagementApi {
  return {
    async list(communityId) {
      const { data, error, response } = await client.GET('/api/members', {
        params: { query: communityId ? { communityId } : {} },
      });
      return data ? { ok: true, members: data } : failure(error, response);
    },
    async get(memberId) {
      const { data, error, response } = await client.GET('/api/members/{memberId}', {
        params: { path: { memberId } },
      });
      return data ? { ok: true, member: data } : failure(error, response);
    },
    async create(input) {
      const { data, error, response } = await client.POST('/api/members', { body: input });
      return data ? { ok: true, member: data } : failure(error, response);
    },
    async rename(memberId, input) {
      const { data, error, response } = await client.PUT('/api/members/{memberId}', {
        params: { path: { memberId } },
        body: input,
      });
      return data ? { ok: true, member: data } : failure(error, response);
    },
    async changeContact(memberId, input) {
      const { data, error, response } = await client.PUT('/api/members/{memberId}/contact', {
        params: { path: { memberId } },
        body: input,
      });
      return data ? { ok: true, member: data } : failure(error, response);
    },
  };
}
