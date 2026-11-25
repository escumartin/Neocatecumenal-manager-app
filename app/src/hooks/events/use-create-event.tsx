import { useCallback, useState } from 'react';
import { eventsApi } from '@/lib/api';
import type { CreateEventInput, EventResult, EventsApi } from '@/lib/api/events-api';

export function useCreateEvent(api: EventsApi = eventsApi) {
  const [submitting, setSubmitting] = useState(false);

  const create = useCallback(
    async (input: CreateEventInput): Promise<EventResult> => {
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
