import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type Me = components['schemas']['Me'];

export type LogInResult =
  | { ok: true; token?: string; me: Me }
  | { ok: false; code: string; retryAfterSeconds?: number };

export type GetMeResult = { ok: true; me: Me } | { ok: false; status: number };

export type FieldError = components['schemas']['ValidationProblem']['errors'][number];

export type OwnActionResult = { ok: true } | { ok: false; status: number; code?: string; fieldErrors?: FieldError[] };

export type SessionApi = {
  logIn: (accessId: number, password: string) => Promise<LogInResult>;
  logOut: () => Promise<void>;
  getMe: () => Promise<GetMeResult>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<OwnActionResult>;
  acceptPrivacyNotice: (version: string) => Promise<OwnActionResult>;
  rejectPrivacyNotice: () => Promise<OwnActionResult>;
};

function ownFailure(error: unknown, response: Response): OwnActionResult {
  const problem = error as { code?: string; errors?: FieldError[] } | undefined;
  return {
    ok: false,
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.errors ? { fieldErrors: problem.errors } : {}),
  };
}

export function createSessionApi(client: ApiClient, tokenDelivery: 'BODY' | 'COOKIE'): SessionApi {
  return {
    async logIn(accessId, password) {
      const { data, error } = await client.POST('/api/sessions', {
        body: { accessId, password, tokenDelivery },
      });
      if (data) {
        return { ok: true, ...(data.token ? { token: data.token } : {}), me: data.me };
      }
      const problem = error as { code?: string; retryAfterSeconds?: number } | undefined;
      return {
        ok: false,
        code: problem?.code ?? 'UNKNOWN',
        ...(problem?.retryAfterSeconds ? { retryAfterSeconds: problem.retryAfterSeconds } : {}),
      };
    },
    async logOut() {
      await client.DELETE('/api/sessions/current');
    },
    async getMe() {
      const { data, response } = await client.GET('/api/me');
      return data ? { ok: true, me: data } : { ok: false, status: response.status };
    },
    async changePassword(currentPassword, newPassword) {
      const { error, response } = await client.PUT('/api/me/password', {
        body: { currentPassword, newPassword },
      });
      return response.ok ? { ok: true } : ownFailure(error, response);
    },
    async acceptPrivacyNotice(version) {
      const { error, response } = await client.POST('/api/me/privacy-notice-acceptance', { body: { version } });
      return response.ok ? { ok: true } : ownFailure(error, response);
    },
    async rejectPrivacyNotice() {
      const { error, response } = await client.POST('/api/me/privacy-notice-rejection');
      return response.ok ? { ok: true } : ownFailure(error, response);
    },
  };
}
