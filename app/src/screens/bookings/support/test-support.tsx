import { PortalHost } from '@rn-primitives/portal';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import { SessionProvider, type SessionApi } from '@/hooks/session/use-session';
import type { BookingRequestsApi } from '@/lib/api/booking-requests-api';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { RoomsApi } from '@/lib/api/rooms-api';

export const communities = [
  { id: '6f1c1f1e-0000-4000-8000-000000000011', number: 1 },
  { id: '6f1c1f1e-0000-4000-8000-000000000012', number: 2 },
];

export const hall = { id: '6f1c1f1e-0000-4000-8000-000000000031', name: 'Salón grande', active: true };
export const chapel = { id: '6f1c1f1e-0000-4000-8000-000000000032', name: 'Capilla', active: true };

export const pending = {
  id: '6f1c1f1e-0000-4000-8000-000000000041',
  roomId: hall.id,
  communityId: communities[0].id,
  date: '2030-05-11',
  startTime: '18:00',
  endTime: '20:00',
  reason: 'Catequesis semanal',
  status: 'PENDING' as const,
  occurrenceCount: 1,
  createdAt: '2030-05-10T08:00:00Z',
};

export const approved = {
  ...pending,
  id: '6f1c1f1e-0000-4000-8000-000000000042',
  roomId: chapel.id,
  date: '2030-05-12',
  reason: 'Convivencia',
  status: 'APPROVED' as const,
};

export const rejected = {
  ...pending,
  id: '6f1c1f1e-0000-4000-8000-000000000043',
  roomId: chapel.id,
  date: '2030-05-13',
  reason: 'Ensayo del coro',
  status: 'REJECTED' as const,
  rejectionReason: 'Salón en obras',
};

export const pendingOfSecondCommunity = {
  ...pending,
  id: '6f1c1f1e-0000-4000-8000-000000000044',
  roomId: chapel.id,
  communityId: communities[1].id,
  date: '2030-05-14',
  reason: 'Reunión de padres',
};

export function fakeBookingRequestsApi(overrides: Partial<BookingRequestsApi> = {}): BookingRequestsApi {
  return {
    create: jest.fn(),
    list: jest.fn().mockResolvedValue({
      ok: true,
      page: { items: [pending, approved], page: 0, size: 100, totalItems: 2 },
    }),
    rectifyReason: jest.fn(),
    approve: jest.fn(),
    reject: jest.fn(),
    get: jest.fn(),
    cancel: jest.fn(),
    cancelBooking: jest.fn(),
    ...overrides,
  };
}

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

export function fakeCommunitiesApi(): CommunitiesApi {
  return { list: jest.fn().mockResolvedValue({ ok: true, communities }) };
}

export async function renderAs(role: 'REGISTERED' | 'MODERATOR' | 'ADMINISTRATOR', element: ReactElement) {
  const me = {
    userId: '6f1c1f1e-0000-4000-8000-000000000001',
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
      {element}
      <PortalHost />
    </SessionProvider>,
  );
}
