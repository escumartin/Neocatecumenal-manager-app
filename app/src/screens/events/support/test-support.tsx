import type { AgendaEvent, EventsApi } from '@/lib/api/events-api';
import { hall } from '@/screens/bookings/support/test-support';

export const wordEvent: AgendaEvent = {
  id: '6f1c1f1e-0000-4000-8000-000000000051',
  type: 'WORD',
  title: 'Palabra de apertura',
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  roomId: hall.id,
  date: '2030-05-11',
  startTime: '18:00',
  endTime: '20:00',
  bookingRequestId: '6f1c1f1e-0000-4000-8000-000000000041',
  bookingStatus: 'PENDING',
};

export function fakeEventsApi(overrides: Partial<EventsApi> = {}): EventsApi {
  return {
    create: jest.fn().mockResolvedValue({ ok: true, event: wordEvent }),
    get: jest.fn().mockResolvedValue({ ok: true, event: wordEvent }),
    ...overrides,
  };
}
