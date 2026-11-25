import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useEvent } from '../use-event';
import type { EventsApi } from '@/lib/api/events-api';

const event = {
  id: '6f1c1f1e-0000-4000-8000-000000000051',
  type: 'WORD' as const,
  title: 'Palabra de apertura',
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  roomId: '6f1c1f1e-0000-4000-8000-000000000031',
  date: '2030-05-11',
  startTime: '18:00',
  endTime: '20:00',
  bookingRequestId: '6f1c1f1e-0000-4000-8000-000000000041',
  bookingStatus: 'PENDING' as const,
};

function fakeApi(overrides: Partial<EventsApi> = {}): EventsApi {
  return {
    create: jest.fn(),
    get: jest.fn().mockResolvedValue({ ok: true, event }),
    ...overrides,
  };
}

describe('useEvent', () => {
  it('loads the event with its booking status', async () => {
    const api = fakeApi();
    const { result } = await renderHook(() => useEvent(event.id, api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.event).toEqual(event);
    expect(api.get).toHaveBeenCalledWith(event.id);
  });

  it('is loading while the server answers', async () => {
    const api = fakeApi({ get: jest.fn().mockReturnValue(new Promise(() => undefined)) });

    const { result } = await renderHook(() => useEvent(event.id, api));

    expect(result.current.status).toBe('loading');
    expect(result.current.event).toBeNull();
  });

  it('reports an unknown event as not found', async () => {
    const api = fakeApi({ get: jest.fn().mockResolvedValue({ ok: false, status: 404, code: 'NOT_FOUND' }) });

    const { result } = await renderHook(() => useEvent(event.id, api));

    await waitFor(() => expect(result.current.status).toBe('notFound'));
    expect(result.current.event).toBeNull();
  });

  it('reports an event of another community as forbidden', async () => {
    const api = fakeApi({ get: jest.fn().mockResolvedValue({ ok: false, status: 403, code: 'FORBIDDEN' }) });

    const { result } = await renderHook(() => useEvent(event.id, api));

    await waitFor(() => expect(result.current.status).toBe('forbidden'));
  });

  it('reports any other failure as an error', async () => {
    const api = fakeApi({ get: jest.fn().mockResolvedValue({ ok: false, status: 500 }) });

    const { result } = await renderHook(() => useEvent(event.id, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
  });

  it('does not query anything while disabled', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useEvent(event.id, api, false));

    expect(result.current.status).toBe('idle');
    expect(api.get).not.toHaveBeenCalled();
  });

  it('reloads the event after an error', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, event });
    const api = fakeApi({ get });
    const { result } = await renderHook(() => useEvent(event.id, api));
    await waitFor(() => expect(result.current.status).toBe('error'));

    await act(async () => {
      await result.current.reload();
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.event).toEqual(event);
  });

  it('reports an error when the request fails', async () => {
    const api = fakeApi({ get: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await renderHook(() => useEvent(event.id, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.event).toBeNull();
  });

  it('reports an error when a reload request fails', async () => {
    const api = fakeApi({
      get: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, event })
        .mockRejectedValueOnce(new Error('network down')),
    });
    const { result } = await renderHook(() => useEvent(event.id, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.reload());

    expect(result.current.status).toBe('error');
  });
});
