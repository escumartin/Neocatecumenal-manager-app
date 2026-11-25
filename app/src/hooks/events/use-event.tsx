import { useCallback, useEffect, useState } from 'react';
import { eventsApi } from '@/lib/api';
import type { AgendaEvent, EventResult, EventsApi } from '@/lib/api/events-api';

export type EventStatus = 'idle' | 'loading' | 'ready' | 'notFound' | 'forbidden' | 'error';

type State = { status: EventStatus; event: AgendaEvent | null };

function toState(result: EventResult): State {
  if (result.ok) {
    return { status: 'ready', event: result.event };
  }
  if (result.status === 404) {
    return { status: 'notFound', event: null };
  }
  if (result.status === 403) {
    return { status: 'forbidden', event: null };
  }
  return { status: 'error', event: null };
}

const FAILED: State = { status: 'error', event: null };

export function useEvent(eventId: string, api: EventsApi = eventsApi, enabled = true) {
  const [state, setState] = useState<State>({
    status: enabled ? 'loading' : 'idle',
    event: null,
  });

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .get(eventId)
      .then(toState, () => FAILED)
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, eventId, enabled]);

  const reload = useCallback(async () => {
    setState((current) => ({ ...current, status: 'loading' }));
    try {
      setState(toState(await api.get(eventId)));
    } catch {
      setState(FAILED);
    }
  }, [api, eventId]);

  return { status: state.status, event: state.event, reload };
}
