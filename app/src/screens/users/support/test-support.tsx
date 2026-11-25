import { PortalHost } from '@rn-primitives/portal';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import { SessionProvider, type SessionApi } from '@/hooks/session/use-session';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { UsersApi } from '@/lib/api/users-api';

export const communities = [
  { id: '6f1c1f1e-0000-4000-8000-000000000011', number: 1 },
  { id: '6f1c1f1e-0000-4000-8000-000000000012', number: 2 },
];

export const ownUserId = '6f1c1f1e-0000-4000-8000-000000000001';

export const ana = {
  id: '6f1c1f1e-0000-4000-8000-000000000021',
  accessId: 482913,
  name: 'Ana',
  role: 'REGISTERED' as const,
  communityId: communities[0].id,
  passwordChangePending: false,
};

export const luis = { ...ana, id: '6f1c1f1e-0000-4000-8000-000000000022', accessId: 482914, name: 'Luis' };

export function fakeUsersApi(overrides: Partial<UsersApi> = {}): UsersApi {
  return {
    list: jest.fn().mockResolvedValue({ ok: true, users: [ana, luis] }),
    get: jest.fn().mockResolvedValue({ ok: true, user: ana }),
    create: jest.fn(),
    update: jest.fn(),
    remove: jest.fn(),
    resetPassword: jest.fn(),
    ...overrides,
  };
}

export const communitiesApi: CommunitiesApi = {
  list: jest.fn().mockResolvedValue({ ok: true, communities }),
};

export async function renderAs(role: 'REGISTERED' | 'MODERATOR' | 'ADMINISTRATOR', screen: ReactElement) {
  const me = {
    userId: ownUserId,
    accessId: 111111,
    name: 'Test User',
    role,
    communityId: communities[0].id,
    communityNumber: 1,
    pendingSteps: [],
    currentPrivacyNoticeVersion: '1',
  };
  const sessionApi = {
    logIn: jest.fn(),
    logOut: jest.fn(),
    getMe: jest.fn().mockResolvedValue({ ok: true, me }),
  } as unknown as SessionApi;
  const store = { read: jest.fn().mockResolvedValue(null), save: jest.fn(), clear: jest.fn() };
  return render(
    <SessionProvider api={sessionApi} store={store}>
      {screen}
      <PortalHost />
    </SessionProvider>,
  );
}
