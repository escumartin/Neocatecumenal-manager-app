import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { communitiesApi } from '@/lib/api';
import type {
  CommunitiesApi,
  Community,
  ListCommunitiesOptions,
  ListCommunitiesResult,
} from '@/lib/api/communities-api';

export type CommunitiesStatus = 'idle' | 'loading' | 'ready' | 'error';

type State = { status: CommunitiesStatus; communities: Community[] };

function toState(result: ListCommunitiesResult): State {
  return result.ok ? { status: 'ready', communities: result.communities } : { status: 'error', communities: [] };
}

const FAILED: State = { status: 'error', communities: [] };

export function useCommunities(
  api: CommunitiesApi = communitiesApi,
  enabled = true,
  { includeDeactivated = false }: ListCommunitiesOptions = {},
) {
  const query = useMemo(() => (includeDeactivated ? { includeDeactivated: true } : undefined), [includeDeactivated]);
  const [state, setState] = useState<State>({ status: enabled ? 'loading' : 'idle', communities: [] });

  const sequence = useRef(0);

  useEffect(() => {
    const id = ++sequence.current;
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .list(query)
      .then(toState, () => FAILED)
      .then((next) => {
        if (active && id === sequence.current) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, enabled, query]);

  const reload = useCallback(async () => {
    const id = ++sequence.current;
    setState((current) => ({ ...current, status: 'loading' }));
    let next = FAILED;
    try {
      next = toState(await api.list(query));
    } catch {
      next = FAILED;
    }
    if (id === sequence.current) {
      setState(next);
    }
  }, [api, query]);

  const refresh = useCallback(async () => {
    if (!enabled) {
      return;
    }
    const id = ++sequence.current;
    try {
      const result = await api.list(query);
      if (result.ok && id === sequence.current) {
        setState(toState(result));
      }
    } catch {
      return;
    }
  }, [api, enabled, query]);

  return { status: state.status, communities: state.communities, reload, refresh };
}
