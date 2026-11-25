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

function deferred() {
  let resolve: (value: unknown) => void = () => undefined;
  const promise = new Promise((r) => (resolve = r));
  return { promise, resolve };
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

  it('keeps the data and the ready status while a silent refresh is pending', async () => {
    let resolveRefresh: (value: unknown) => void = () => undefined;
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, page: { items: [{ id: 'first' }] } })
        .mockReturnValueOnce(new Promise((resolve) => (resolveRefresh = resolve))),
    });
    const { result } = await renderHook(() => useBookingRequests(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.requests).toEqual([{ id: 'first' }]);

    await act(async () => {
      resolveRefresh({ ok: true, page: { items: [{ id: 'second' }] } });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.requests).toEqual([{ id: 'second' }]);
  });

  it('keeps the data when a silent refresh fails', async () => {
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, page: { items: [{ id: 'first' }] } })
        .mockRejectedValueOnce(new Error('network down')),
    });
    const { result } = await renderHook(() => useBookingRequests(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.status).toBe('ready');
    expect(result.current.requests).toEqual([{ id: 'first' }]);
  });

  it('keeps the data when a silent refresh is answered with a failure', async () => {
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, page: { items: [{ id: 'first' }] } })
        .mockResolvedValueOnce({ ok: false, status: 500 }),
    });
    const { result } = await renderHook(() => useBookingRequests(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.status).toBe('ready');
    expect(result.current.requests).toEqual([{ id: 'first' }]);
  });

  it('ignores a stale refresh that resolves after a newer reload', async () => {
    const stale = deferred();
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, page: { items: [{ id: 'first' }] } })
        .mockReturnValueOnce(stale.promise)
        .mockResolvedValueOnce({ ok: true, page: { items: [{ id: 'newest' }] } }),
    });
    const { result } = await renderHook(() => useBookingRequests(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });
    await act(() => result.current.reload());
    await act(async () => {
      stale.resolve({ ok: true, page: { items: [{ id: 'stale' }] } });
      await refreshing;
    });

    expect(result.current.status).toBe('ready');
    expect(result.current.requests).toEqual([{ id: 'newest' }]);
  });

  it('does not call the server on refresh when disabled', async () => {
    const api = fakeApi();
    const { result } = await renderHook(() => useBookingRequests(api, false));

    await act(() => result.current.refresh());

    expect(api.list).not.toHaveBeenCalled();
    expect(result.current.status).toBe('idle');
  });

  it('ignores a stale refresh that resolves after a cancellation', async () => {
    const stale = deferred();
    const api = fakeApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, page: { items: [{ id: 'a', status: 'PENDING' }] } })
        .mockReturnValueOnce(stale.promise),
      cancel: jest.fn().mockResolvedValue({ ok: true, request: { id: 'a', status: 'CANCELLED' } }),
    });
    const { result } = await renderHook(() => useBookingRequests(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let refreshing: Promise<unknown> = Promise.resolve();
    await act(async () => {
      refreshing = result.current.refresh();
    });
    await act(() => result.current.cancel('a'));
    await act(async () => {
      stale.resolve({ ok: true, page: { items: [{ id: 'a', status: 'PENDING' }] } });
      await refreshing;
    });

    expect(result.current.requests).toEqual([{ id: 'a', status: 'CANCELLED' }]);
  });
});
