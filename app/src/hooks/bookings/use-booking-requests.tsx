import { useCallback, useEffect, useRef, useState } from 'react';
import { bookingRequestsApi } from '@/lib/api';
import type {
  BookingFailure,
  BookingRequest,
  BookingRequestDetailResult,
  BookingRequestResult,
  BookingRequestsApi,
  ListBookingRequestsResult,
} from '@/lib/api/booking-requests-api';

export type BookingRequestsStatus = 'idle' | 'loading' | 'ready' | 'error';

type State = { status: BookingRequestsStatus; requests: BookingRequest[] };

const PAGE_SIZE = 100;
const STALE_CODES = ['BOOKING_ALREADY_STARTED', 'BOOKING_REQUEST_NOT_ACTIVE'];

function toState(result: ListBookingRequestsResult): State {
  return result.ok
    ? { status: 'ready', requests: result.page.items }
    : { status: 'error', requests: [] };
}

const FAILED: State = { status: 'error', requests: [] };

export function useBookingRequests(
  api: BookingRequestsApi = bookingRequestsApi,
  enabled = true,
) {
  const [state, setState] = useState<State>({
    status: enabled ? 'loading' : 'idle',
    requests: [],
  });

  const sequence = useRef(0);

  useEffect(() => {
    const id = ++sequence.current;
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .list({ size: PAGE_SIZE })
      .then(toState, () => FAILED)
      .then((next) => {
        if (active && id === sequence.current) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, enabled]);

  const reload = useCallback(async () => {
    const id = ++sequence.current;
    setState((current) => ({ ...current, status: 'loading' }));
    let next = FAILED;
    try {
      next = toState(await api.list({ size: PAGE_SIZE }));
    } catch {
      next = FAILED;
    }
    if (id === sequence.current) {
      setState(next);
    }
  }, [api]);

  const refresh = useCallback(async () => {
    if (!enabled) {
      return;
    }
    const id = ++sequence.current;
    try {
      const result = await api.list({ size: PAGE_SIZE });
      if (result.ok && id === sequence.current) {
        setState(toState(result));
      }
    } catch {
      return;
    }
  }, [api, enabled]);

  const rectifyReason = useCallback(
    async (bookingRequestId: string, reason: string) => {
      const result = await api.rectifyReason(bookingRequestId, reason);
      if (result.ok) {
        sequence.current++;
        setState((current) => ({
          ...current,
          requests: current.requests.map((request) =>
            request.id === bookingRequestId ? result.request : request,
          ),
        }));
      }
      return result;
    },
    [api],
  );

  const replace = useCallback(
    (bookingRequestId: string, updated: BookingRequest) => {
      sequence.current++;
      setState((current) => ({
        ...current,
        requests: current.requests.map((request) =>
          request.id === bookingRequestId ? updated : request,
        ),
      }));
    },
    [],
  );

  const refreshWhenStale = useCallback(
    async (failure: BookingFailure) => {
      if (failure.code && STALE_CODES.includes(failure.code)) {
        const id = ++sequence.current;
        const next = toState(await api.list({ size: PAGE_SIZE }));
        if (id === sequence.current) {
          setState(next);
        }
      }
    },
    [api],
  );

  const cancel = useCallback(
    async (bookingRequestId: string): Promise<BookingRequestResult> => {
      const result = await api.cancel(bookingRequestId);
      if (result.ok) {
        replace(bookingRequestId, result.request);
      } else {
        await refreshWhenStale(result);
      }
      return result;
    },
    [api, replace, refreshWhenStale],
  );

  const cancelBooking = useCallback(
    async (
      bookingRequestId: string,
      bookingId: string,
    ): Promise<BookingRequestDetailResult> => {
      const result = await api.cancelBooking(bookingRequestId, bookingId);
      if (result.ok) {
        replace(bookingRequestId, result.detail);
      } else {
        await refreshWhenStale(result);
      }
      return result;
    },
    [api, replace, refreshWhenStale],
  );

  const loadDetail = useCallback(
    (bookingRequestId: string) => api.get(bookingRequestId),
    [api],
  );

  return {
    status: state.status,
    requests: state.requests,
    reload,
    refresh,
    rectifyReason,
    cancel,
    cancelBooking,
    loadDetail,
  };
}
