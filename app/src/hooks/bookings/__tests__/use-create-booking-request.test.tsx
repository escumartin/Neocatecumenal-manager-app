import { act, renderHook } from '@testing-library/react-native';
import { useCreateBookingRequest } from '../use-create-booking-request';
import type { BookingRequestsApi, CreateBookingRequestInput } from '@/lib/api/booking-requests-api';

const input: CreateBookingRequestInput = {
  roomId: '6f1c1f1e-0000-4000-8000-000000000031',
  date: '2030-05-11',
  startTime: '18:00',
  endTime: '20:00',
  reason: 'Catequesis semanal',
};

const request = {
  ...input,
  id: '6f1c1f1e-0000-4000-8000-000000000041',
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  status: 'PENDING' as const,
  occurrenceCount: 1,
  createdAt: '2030-05-10T08:00:00Z',
};

function fakeApi(overrides: Partial<BookingRequestsApi> = {}): BookingRequestsApi {
  return {
    create: jest.fn().mockResolvedValue({ ok: true, request }),
    ...overrides,
  } as unknown as BookingRequestsApi;
}

describe('useCreateBookingRequest', () => {
  it('sends the request and returns the created one', async () => {
    const api = fakeApi();
    const { result } = await renderHook(() => useCreateBookingRequest(api));

    let outcome;
    await act(async () => {
      outcome = await result.current.create(input);
    });

    expect(api.create).toHaveBeenCalledWith(input);
    expect(outcome).toEqual({ ok: true, request });
    expect(result.current.submitting).toBe(false);
  });

  it('is submitting while the server answers', async () => {
    let answer: (value: unknown) => void = () => undefined;
    const api = fakeApi({
      create: jest.fn().mockReturnValue(new Promise((resolve) => (answer = resolve))),
    });
    const { result } = await renderHook(() => useCreateBookingRequest(api));

    let pending: Promise<unknown> = Promise.resolve();
    await act(async () => {
      pending = result.current.create(input);
    });
    expect(result.current.submitting).toBe(true);

    await act(async () => {
      answer({ ok: true, request });
      await pending;
    });
    expect(result.current.submitting).toBe(false);
  });

  it('stops submitting and rethrows when the request fails', async () => {
    const api = fakeApi({ create: jest.fn().mockRejectedValue(new Error('network down')) });
    const { result } = await renderHook(() => useCreateBookingRequest(api));

    await act(async () => {
      await expect(result.current.create(input)).rejects.toThrow('network down');
    });

    expect(result.current.submitting).toBe(false);
  });
});
