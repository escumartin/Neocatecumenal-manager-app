import { useCallback, useEffect, useState } from 'react';
import { communitiesApi } from '@/lib/api';
import type { CommunitiesApi, Community, ListCommunitiesResult } from '@/lib/api/communities-api';

export type CommunitiesStatus = 'idle' | 'loading' | 'ready' | 'error';

type State = { status: CommunitiesStatus; communities: Community[] };

function toState(result: ListCommunitiesResult): State {
  return result.ok ? { status: 'ready', communities: result.communities } : { status: 'error', communities: [] };
}

const FAILED: State = { status: 'error', communities: [] };

export function useCommunities(api: CommunitiesApi = communitiesApi, enabled = true) {
  const [state, setState] = useState<State>({ status: enabled ? 'loading' : 'idle', communities: [] });

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .list()
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
      setState(toState(await api.list()));
    } catch {
      setState(FAILED);
    }
  }, [api]);

  return { status: state.status, communities: state.communities, reload };
}
