import { PortalHost } from '@rn-primitives/portal';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import { SessionProvider, type SessionApi } from '@/hooks/session/use-session';
import type { RoomsApi } from '@/lib/api/rooms-api';

export const hall = { id: '6f1c1f1e-0000-4000-8000-000000000031', name: 'Salón grande', active: true };
export const chapel = { id: '6f1c1f1e-0000-4000-8000-000000000032', name: 'Capilla', active: true };

export const emptyOccupancy = { roomId: hall.id, date: '2030-05-11', occupied: [] };

export function fakeRoomsApi(overrides: Partial<RoomsApi> = {}): RoomsApi {
  return {
    list: jest.fn().mockResolvedValue({ ok: true, rooms: [hall, chapel] }),
    create: jest.fn(),
    rename: jest.fn(),
    deactivate: jest.fn(),
    occupancy: jest.fn().mockResolvedValue({ ok: true, occupancy: emptyOccupancy }),
    ...overrides,
  };
}

export async function renderAs(role: 'REGISTERED' | 'MODERATOR' | 'ADMINISTRATOR', element: ReactElement) {
  const me = {
    userId: '6f1c1f1e-0000-4000-8000-000000000001',
    accessId: 111111,
    name: 'Test User',
    role,
    communityId: '6f1c1f1e-0000-4000-8000-000000000011',
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
      {element}
      <PortalHost />
    </SessionProvider>,
  );
}
