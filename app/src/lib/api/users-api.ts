import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type User = components['schemas']['User'];
export type CreatedUser = components['schemas']['CreatedUser'];
export type UserInput = components['schemas']['UserInput'];
export type FieldError = components['schemas']['ValidationProblem']['errors'][number];

export type UsersFailure = { ok: false; status: number; code?: string; fieldErrors?: FieldError[] };

export type ListUsersResult = { ok: true; users: User[] } | UsersFailure;
export type GetUserResult = { ok: true; user: User } | UsersFailure;
export type CreateUserResult = { ok: true; user: CreatedUser } | UsersFailure;
export type RemoveUserResult = { ok: true } | UsersFailure;
export type ResetPasswordResult = { ok: true; initialPassword: string } | UsersFailure;

export type UsersApi = {
  list: (communityId?: string) => Promise<ListUsersResult>;
  get: (userId: string) => Promise<GetUserResult>;
  create: (input: UserInput) => Promise<CreateUserResult>;
  update: (userId: string, input: UserInput) => Promise<GetUserResult>;
  remove: (userId: string) => Promise<RemoveUserResult>;
  resetPassword: (userId: string) => Promise<ResetPasswordResult>;
};

function failure(error: unknown, response: Response): UsersFailure {
  const problem = error as { code?: string; errors?: FieldError[] } | undefined;
  return {
    ok: false,
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.errors ? { fieldErrors: problem.errors } : {}),
  };
}

export function createUsersApi(client: ApiClient): UsersApi {
  return {
    async list(communityId) {
      const { data, error, response } = await client.GET('/api/users', {
        params: { query: communityId ? { communityId } : {} },
      });
      return data ? { ok: true, users: data.items } : failure(error, response);
    },
    async get(userId) {
      const { data, error, response } = await client.GET('/api/users/{userId}', {
        params: { path: { userId } },
      });
      return data ? { ok: true, user: data } : failure(error, response);
    },
    async create(input) {
      const { data, error, response } = await client.POST('/api/users', { body: input });
      return data ? { ok: true, user: data } : failure(error, response);
    },
    async update(userId, input) {
      const { data, error, response } = await client.PUT('/api/users/{userId}', {
        params: { path: { userId } },
        body: input,
      });
      return data ? { ok: true, user: data } : failure(error, response);
    },
    async remove(userId) {
      const { error, response } = await client.DELETE('/api/users/{userId}', {
        params: { path: { userId } },
      });
      return response.ok ? { ok: true } : failure(error, response);
    },
    async resetPassword(userId) {
      const { data, error, response } = await client.POST('/api/users/{userId}/password-reset', {
        params: { path: { userId } },
      });
      return data ? { ok: true, initialPassword: data.initialPassword } : failure(error, response);
    },
  };
}
