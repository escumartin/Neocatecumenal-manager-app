import { PortalHost } from '@rn-primitives/portal';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import { SessionProvider, type SessionApi } from '@/hooks/session/use-session';
import type {
  CommunitiesApi,
  Community,
  CommunityDetail,
  CommunityManagementApi,
} from '@/lib/api/communities-api';

export const twelve: Community = {
  id: '6f1c1f1e-0000-4000-8000-000000000031',
  number: 12,
  name: 'San Pedro',
  status: 'ACTIVE',
  hasResponsible: false,
};

export const five: Community = {
  id: '6f1c1f1e-0000-4000-8000-000000000032',
  number: 5,
  status: 'ACTIVE',
  hasResponsible: true,
};

export const closed: Community = {
  id: '6f1c1f1e-0000-4000-8000-000000000033',
  number: 3,
  name: 'Santa Ana',
  status: 'DEACTIVATED',
  hasResponsible: false,
};

export function detailOf(community: Community): CommunityDetail {
  return {
    id: community.id,
    number: community.number,
    ...(community.name ? { name: community.name } : {}),
    status: community.status ?? 'ACTIVE',
    hasResponsible: community.hasResponsible ?? false,
    responsibleIsModerator: false,
  };
}

export function fakeCommunitiesApi(
  overrides: Partial<CommunitiesApi & CommunityManagementApi> = {},
): CommunitiesApi & CommunityManagementApi {
  return {
    list: jest.fn().mockResolvedValue({ ok: true, communities: [closed, five, twelve] }),
    get: jest.fn().mockResolvedValue({ ok: true, community: detailOf(twelve) }),
    create: jest.fn().mockResolvedValue({ ok: true, community: detailOf(twelve) }),
    update: jest.fn().mockResolvedValue({ ok: true, community: detailOf(twelve) }),
    remove: jest.fn().mockResolvedValue({ ok: true }),
    deactivate: jest.fn().mockResolvedValue({ ok: true }),
    reactivate: jest.fn().mockResolvedValue({ ok: true }),
    ...overrides,
  };
}

export async function renderAs(role: 'REGISTERED' | 'MODERATOR' | 'ADMINISTRATOR', element: ReactElement) {
  const me = {
    userId: '6f1c1f1e-0000-4000-8000-000000000001',
    accessId: 111111,
    name: 'Test User',
    role,
    communityId: twelve.id,
    communityNumber: 12,
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
      {element}
      <PortalHost />
    </SessionProvider>,
  );
}
