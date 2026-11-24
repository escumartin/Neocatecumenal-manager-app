import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import { SessionProvider, type SessionApi } from '@/hooks/session/use-session';
import type { Me } from '@/lib/api/session-api';

export const baseMe: Me = {
  userId: '6f1c1f1e-0000-4000-8000-000000000001',
  accessId: 482913,
  name: 'Test User',
  role: 'REGISTERED',
  communityId: '6f1c1f1e-0000-4000-8000-000000000002',
  communityNumber: 3,
  pendingSteps: [],
  currentPrivacyNoticeVersion: '2',
};

export function fakeSessionApi(overrides: Partial<SessionApi> = {}): SessionApi {
  return {
    logIn: jest.fn(),
    logOut: jest.fn().mockResolvedValue(undefined),
    getMe: jest.fn().mockResolvedValue({ ok: true, me: baseMe }),
    changePassword: jest.fn().mockResolvedValue({ ok: true }),
    acceptPrivacyNotice: jest.fn().mockResolvedValue({ ok: true }),
    rejectPrivacyNotice: jest.fn().mockResolvedValue({ ok: true }),
    ...overrides,
  };
}

export function sessionWith(me: Partial<Me> | null, overrides: Partial<SessionApi> = {}) {
  const api = fakeSessionApi({
    getMe: jest.fn().mockResolvedValue(me ? { ok: true, me: { ...baseMe, ...me } } : { ok: false, status: 401 }),
    ...overrides,
  });
  const store = { read: jest.fn().mockResolvedValue(null), save: jest.fn(), clear: jest.fn() };
  return { api, store };
}

export async function renderWithSession(
  ui: ReactElement,
  me: Partial<Me> | null = {},
  overrides: Partial<SessionApi> = {},
) {
  const { api, store } = sessionWith(me, overrides);
  await render(
    <SessionProvider api={api} store={store}>
      {ui}
    </SessionProvider>,
  );
  return { api, store };
}
