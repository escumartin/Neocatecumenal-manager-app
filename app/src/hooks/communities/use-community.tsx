import { useCallback, useEffect, useRef, useState } from 'react';
import { communitiesApi } from '@/lib/api';
import type { CommunityDetail, CommunityManagementApi, CommunityOutcome } from '@/lib/api/communities-api';

export type CommunityStatus = 'loading' | 'ready' | 'error';

type State = { status: CommunityStatus; community: CommunityDetail | null };

export function useCommunity(communityId: string, api: CommunityManagementApi = communitiesApi) {
  const [state, setState] = useState<State>({ status: 'loading', community: null });

  const sequence = useRef(0);

  const load = useCallback(async () => {
    const id = ++sequence.current;
    let next: State = { status: 'error', community: null };
    try {
      const result = await api.get(communityId);
      if (result.ok) {
        next = { status: 'ready', community: result.community };
      }
    } catch {
      next = { status: 'error', community: null };
    }
    if (id === sequence.current) {
      setState(next);
    }
  }, [api, communityId]);

  useEffect(() => {
    const id = ++sequence.current;
    let active = true;
    api
      .get(communityId)
      .then((result) => (result.ok ? ({ status: 'ready', community: result.community } as const) : null))
      .catch(() => null)
      .then((next) => {
        if (active && id === sequence.current) {
          setState(next ?? { status: 'error', community: null });
        }
      });
    return () => {
      active = false;
    };
  }, [api, communityId]);

  const reload = useCallback(async () => {
    setState({ status: 'loading', community: null });
    await load();
  }, [load]);

  const refresh = useCallback(async () => {
    const id = ++sequence.current;
    try {
      const result = await api.get(communityId);
      if (result.ok && id === sequence.current) {
        setState({ status: 'ready', community: result.community });
      }
    } catch {
      return;
    }
  }, [api, communityId]);

  const refreshAfter = useCallback(
    async (outcome: CommunityOutcome) => {
      if (outcome.ok) {
        await load();
      }
      return outcome;
    },
    [load],
  );

  const deactivate = useCallback(
    async () => refreshAfter(await api.deactivate(communityId, true)),
    [api, communityId, refreshAfter],
  );

  const reactivate = useCallback(
    async () => refreshAfter(await api.reactivate(communityId)),
    [api, communityId, refreshAfter],
  );

  const remove = useCallback(() => api.remove(communityId), [api, communityId]);

  return { status: state.status, community: state.community, reload, refresh, deactivate, reactivate, remove };
}
