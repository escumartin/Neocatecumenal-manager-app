import { useCallback, useEffect, useRef, useState } from 'react';
import { bookingRequestsApi } from '@/lib/api';
import type {
  BookingRequest,
  BookingRequestResult,
  BookingRequestsApi,
  ListBookingRequestsResult,
} from '@/lib/api/booking-requests-api';

export type PendingRequestsStatus = 'idle' | 'loading' | 'ready' | 'error';

type State = { status: PendingRequestsStatus; requests: BookingRequest[] };

const PAGE_SIZE = 100;
const QUERY = { status: ['PENDING' as const], size: PAGE_SIZE };

function toState(result: ListBookingRequestsResult): State {
  return result.ok
    ? { status: 'ready', requests: result.page.items }
    : { status: 'error', requests: [] };
}

const FAILED: State = { status: 'error', requests: [] };

export function usePendingRequests(
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
      .list(QUERY)
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
      next = toState(await api.list(QUERY));
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
      const result = await api.list(QUERY);
      if (result.ok && id === sequence.current) {
        setState(toState(result));
      }
    } catch {
      return;
    }
  }, [api, enabled]);

  const settle = useCallback(
    async (bookingRequestId: string, result: BookingRequestResult) => {
      if (result.ok) {
        sequence.current++;
        setState((current) => ({
          ...current,
          requests: current.requests.filter(
            (request) => request.id !== bookingRequestId,
          ),
        }));
      } else if (result.code === 'BOOKING_REQUEST_ALREADY_RESOLVED') {
        const id = ++sequence.current;
        const next = toState(await api.list(QUERY));
        if (id === sequence.current) {
          setState(next);
        }
      }
      return result;
    },
    [api],
  );

  const approve = useCallback(
    async (bookingRequestId: string) =>
      settle(bookingRequestId, await api.approve(bookingRequestId)),
    [api, settle],
  );

  const reject = useCallback(
    async (bookingRequestId: string, reason?: string) =>
      settle(bookingRequestId, await api.reject(bookingRequestId, reason)),
    [api, settle],
  );

  return {
    status: state.status,
    requests: state.requests,
    reload,
    refresh,
    approve,
    reject,
  };
}
