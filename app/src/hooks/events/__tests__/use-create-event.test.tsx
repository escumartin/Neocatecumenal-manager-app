import { act, renderHook } from '@testing-library/react-native';
import { useCreateEvent } from '../use-create-event';
import type { CreateEventInput, EventsApi } from '@/lib/api/events-api';

const input: CreateEventInput = {
  type: 'CONVIVENCE',
  title: 'Convivencia de inicio de curso',
  roomId: '6f1c1f1e-0000-4000-8000-000000000031',
  date: '2030-05-11',
  startTime: '09:00',
  endTime: '18:00',
};

const event = {
  ...input,
  id: '6f1c1f1e-0000-4000-8000-000000000051',
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  bookingRequestId: '6f1c1f1e-0000-4000-8000-000000000041',
  bookingStatus: 'PENDING' as const,
};

function fakeApi(overrides: Partial<EventsApi> = {}): EventsApi {
  return { create: jest.fn().mockResolvedValue({ ok: true, event }), get: jest.fn(), ...overrides };
}

describe('useCreateEvent', () => {
  it('sends the event and returns the created one', async () => {
    const api = fakeApi();
    const { result } = await renderHook(() => useCreateEvent(api));

    let outcome;
    await act(async () => {
      outcome = await result.current.create(input);
    });

    expect(api.create).toHaveBeenCalledWith(input);
    expect(outcome).toEqual({ ok: true, event });
    expect(result.current.submitting).toBe(false);
  });

  it('is submitting while the server answers', async () => {
    let answer: (value: unknown) => void = () => undefined;
    const create = jest.fn().mockReturnValue(new Promise((resolve) => (answer = resolve)));
    const api = fakeApi({ create });
    const { result } = await renderHook(() => useCreateEvent(api));

    let pending: Promise<unknown> = Promise.resolve();
    await act(async () => {
      pending = result.current.create(input);
    });
    expect(result.current.submitting).toBe(true);

    await act(async () => {
      answer({ ok: true, event });
      await pending;
    });
    expect(result.current.submitting).toBe(false);
  });

  it('returns the failure of a refused event', async () => {
    const failure = { ok: false as const, status: 409, code: 'ROOM_OCCUPIED', conflicts: [] };
    const api = fakeApi({ create: jest.fn().mockResolvedValue(failure) });
    const { result } = await renderHook(() => useCreateEvent(api));

    let outcome;
    await act(async () => {
      outcome = await result.current.create(input);
    });

    expect(outcome).toEqual(failure);
    expect(result.current.submitting).toBe(false);
  });

  it('stops submitting and rethrows when the call itself fails', async () => {
    const api = fakeApi({ create: jest.fn().mockRejectedValue(new Error('network down')) });
    const { result } = await renderHook(() => useCreateEvent(api));

    await act(async () => {
      await expect(result.current.create(input)).rejects.toThrow('network down');
    });

    expect(result.current.submitting).toBe(false);
  });
});
