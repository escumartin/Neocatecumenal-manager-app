import { useCallback, useState } from 'react';
import { bookingRequestsApi } from '@/lib/api';
import type {
  BookingRequestResult,
  BookingRequestsApi,
  CreateBookingRequestInput,
} from '@/lib/api/booking-requests-api';

export function useCreateBookingRequest(api: BookingRequestsApi = bookingRequestsApi) {
  const [submitting, setSubmitting] = useState(false);

  const create = useCallback(
    async (input: CreateBookingRequestInput): Promise<BookingRequestResult> => {
      setSubmitting(true);
      try {
        return await api.create(input);
      } finally {
        setSubmitting(false);
      }
    },
    [api],
  );

  return { submitting, create };
}
