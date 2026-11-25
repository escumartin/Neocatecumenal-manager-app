import type { components } from '@/api/schema';
import type { ApiClient } from './client';

export type BookingRequest = components['schemas']['BookingRequest'];
export type BookingRequestDetail =
  components['schemas']['BookingRequestDetail'];
export type BookingRequestPage = components['schemas']['BookingRequestPage'];
export type BookingRequestStatus =
  components['schemas']['BookingRequestStatus'];
export type CreateBookingRequestInput =
  components['schemas']['CreateBookingRequest'];
export type BookingFieldError =
  components['schemas']['ValidationProblem']['errors'][number];
export type BookingConflict = NonNullable<
  components['schemas']['RoomOccupiedProblem']['conflicts']
>[number];

export type BookingFailure = {
  ok: false;
  status: number;
  code?: string;
  fieldErrors?: BookingFieldError[];
  conflicts?: BookingConflict[];
};

export type BookingRequestResult =
  { ok: true; request: BookingRequest } | BookingFailure;
export type BookingRequestDetailResult =
  { ok: true; detail: BookingRequestDetail } | BookingFailure;
export type ListBookingRequestsResult =
  { ok: true; page: BookingRequestPage } | { ok: false; status: number };

export type ListBookingRequestsQuery = {
  status?: BookingRequestStatus[];
  communityId?: string;
  page?: number;
  size?: number;
};

export type BookingRequestsApi = {
  create: (input: CreateBookingRequestInput) => Promise<BookingRequestResult>;
  list: (
    query?: ListBookingRequestsQuery,
  ) => Promise<ListBookingRequestsResult>;
  rectifyReason: (
    bookingRequestId: string,
    reason: string,
  ) => Promise<BookingRequestResult>;
  approve: (bookingRequestId: string) => Promise<BookingRequestResult>;
  reject: (
    bookingRequestId: string,
    reason?: string,
  ) => Promise<BookingRequestResult>;
  get: (bookingRequestId: string) => Promise<BookingRequestDetailResult>;
  cancel: (bookingRequestId: string) => Promise<BookingRequestResult>;
  cancelBooking: (
    bookingRequestId: string,
    bookingId: string,
  ) => Promise<BookingRequestDetailResult>;
};

function failure(error: unknown, response: Response): BookingFailure {
  const problem = error as
    | {
        code?: string;
        errors?: BookingFieldError[];
        conflicts?: BookingConflict[];
      }
    | undefined;
  return {
    ok: false,
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.errors ? { fieldErrors: problem.errors } : {}),
    ...(problem?.conflicts ? { conflicts: problem.conflicts } : {}),
  };
}

export function createBookingRequestsApi(
  client: ApiClient,
): BookingRequestsApi {
  return {
    async create(input) {
      const { data, error, response } = await client.POST(
        '/api/booking-requests',
        { body: input },
      );
      return data ? { ok: true, request: data } : failure(error, response);
    },
    async list(query = {}) {
      const { data, response } = await client.GET('/api/booking-requests', {
        params: { query },
      });
      return data
        ? { ok: true, page: data }
        : { ok: false, status: response.status };
    },
    async rectifyReason(bookingRequestId, reason) {
      const { data, error, response } = await client.PUT(
        '/api/booking-requests/{bookingRequestId}/reason',
        {
          params: { path: { bookingRequestId } },
          body: { reason },
        },
      );
      return data ? { ok: true, request: data } : failure(error, response);
    },
    async approve(bookingRequestId) {
      const { data, error, response } = await client.POST(
        '/api/booking-requests/{bookingRequestId}/approval',
        {
          params: { path: { bookingRequestId } },
        },
      );
      return data ? { ok: true, request: data } : failure(error, response);
    },
    async reject(bookingRequestId, reason) {
      const { data, error, response } = await client.POST(
        '/api/booking-requests/{bookingRequestId}/rejection',
        {
          params: { path: { bookingRequestId } },
          body: reason === undefined ? {} : { reason },
        },
      );
      return data ? { ok: true, request: data } : failure(error, response);
    },
    async get(bookingRequestId) {
      const { data, error, response } = await client.GET(
        '/api/booking-requests/{bookingRequestId}',
        {
          params: { path: { bookingRequestId } },
        },
      );
      return data ? { ok: true, detail: data } : failure(error, response);
    },
    async cancel(bookingRequestId) {
      const { data, error, response } = await client.POST(
        '/api/booking-requests/{bookingRequestId}/cancellation',
        {
          params: { path: { bookingRequestId } },
        },
      );
      return data ? { ok: true, request: data } : failure(error, response);
    },
    async cancelBooking(bookingRequestId, bookingId) {
      const { data, error, response } = await client.POST(
        '/api/booking-requests/{bookingRequestId}/bookings/{bookingId}/cancellation',
        { params: { path: { bookingRequestId, bookingId } } },
      );
      return data ? { ok: true, detail: data } : failure(error, response);
    },
  };
}
