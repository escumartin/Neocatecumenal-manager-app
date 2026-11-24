import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from '@/api/schema';

export const PENDING_STEP_CODES = [
  'PRIVACY_NOTICE_PENDING',
  'PRIVACY_NOTICE_OUTDATED',
  'PASSWORD_CHANGE_REQUIRED',
] as const;

export type PendingStepCode = (typeof PENDING_STEP_CODES)[number];

export type ApiClientOptions = {
  baseUrl: string;
  readToken: () => Promise<string | null>;
  onUnauthorized: () => void;
  onPendingStep: (code: PendingStepCode) => void;
  fetch?: typeof globalThis.fetch;
};

async function problemCode(response: Response): Promise<string | undefined> {
  try {
    const body = (await response.clone().json()) as { code?: string };
    return body.code;
  } catch {
    return undefined;
  }
}

function isPendingStep(code: string | undefined): code is PendingStepCode {
  return PENDING_STEP_CODES.some((candidate) => candidate === code);
}

export function createApiClient(options: ApiClientOptions) {
  const middleware: Middleware = {
    async onRequest({ request }) {
      const token = await options.readToken();
      if (token) {
        request.headers.set('Authorization', `Bearer ${token}`);
      }
      return request;
    },
    async onResponse({ response }) {
      if (response.status === 401) {
        if ((await problemCode(response)) !== 'INVALID_CREDENTIALS') {
          options.onUnauthorized();
        }
      } else if (response.status === 403) {
        const code = await problemCode(response);
        if (isPendingStep(code)) {
          options.onPendingStep(code);
        }
      }
      return response;
    },
  };

  const client = createClient<paths>({
    baseUrl: options.baseUrl,
    credentials: 'include',
    ...(options.fetch ? { fetch: options.fetch } : {}),
  });
  client.use(middleware);
  return client;
}

export type ApiClient = ReturnType<typeof createApiClient>;
