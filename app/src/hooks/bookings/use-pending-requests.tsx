import { useCallback, useEffect, useState } from 'react';
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

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .list(QUERY)
      .then(toState, () => FAILED)
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, enabled]);

  const reload = useCallback(async () => {
    setState((current) => ({ ...current, status: 'loading' }));
    try {
      setState(toState(await api.list(QUERY)));
    } catch {
      setState(FAILED);
    }
  }, [api]);

  const settle = useCallback(
    async (bookingRequestId: string, result: BookingRequestResult) => {
      if (result.ok) {
        setState((current) => ({
          ...current,
          requests: current.requests.filter(
            (request) => request.id !== bookingRequestId,
          ),
        }));
      } else if (result.code === 'BOOKING_REQUEST_ALREADY_RESOLVED') {
        setState(toState(await api.list(QUERY)));
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
    approve,
    reject,
  };
}
