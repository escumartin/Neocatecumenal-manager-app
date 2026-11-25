import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useBookingRequests } from '../use-booking-requests';
import type { BookingRequestsApi } from '@/lib/api/booking-requests-api';

function fakeApi(overrides: Partial<BookingRequestsApi> = {}): BookingRequestsApi {
  return {
    create: jest.fn(),
    list: jest.fn().mockResolvedValue({ ok: true, page: { items: [] } }),
    get: jest.fn(),
    rectifyReason: jest.fn(),
    cancel: jest.fn(),
    cancelBooking: jest.fn(),
    approve: jest.fn(),
    reject: jest.fn(),
    ...overrides,
  } as unknown as BookingRequestsApi;
}

describe('useBookingRequests', () => {
  it('reports an error when the list request fails', async () => {
    const api = fakeApi({ list: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await renderHook(() => useBookingRequests(api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.requests).toEqual([]);
  });

  it('reports an error when a reload request fails', async () => {
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, page: { items: [] } })
        .mockRejectedValueOnce(new Error('network down')),
    });
    const { result } = await renderHook(() => useBookingRequests(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.reload());

    expect(result.current.status).toBe('error');
  });
});
