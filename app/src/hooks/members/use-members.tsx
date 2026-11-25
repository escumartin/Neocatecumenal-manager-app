import { useCallback, useEffect, useState } from 'react';
import { membersApi } from '@/lib/api';
import type { ListMembersResult, Member, MembersApi } from '@/lib/api/members-api';

export type MembersStatus = 'idle' | 'loading' | 'ready' | 'error';

type State = { key: string | null; status: MembersStatus; members: Member[] };

function toState(key: string, result: ListMembersResult): State {
  return result.ok ? { key, status: 'ready', members: result.members } : { key, status: 'error', members: [] };
}

export function useMembers(communityId: string | undefined, api: MembersApi = membersApi, enabled = true) {
  const key = communityId ?? '';
  const [state, setState] = useState<State>({ key: null, status: 'idle', members: [] });
  const current: State =
    state.key === key ? state : { key, status: enabled ? 'loading' : 'idle', members: [] };

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .list(communityId)
      .then((result) => toState(key, result), () => toState(key, { ok: false, status: 0 }))
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, communityId, enabled, key]);

  const reload = useCallback(async () => {
    setState({ key, status: 'loading', members: [] });
    try {
      setState(toState(key, await api.list(communityId)));
    } catch {
      setState(toState(key, { ok: false, status: 0 }));
    }
  }, [api, communityId, key]);

  const refresh = useCallback(async () => {
    if (!enabled) {
      return;
    }
    try {
      const result = await api.list(communityId);
      if (result.ok) {
        setState(toState(key, result));
      }
    } catch {
      return;
    }
  }, [api, communityId, enabled, key]);

  return { status: current.status, members: current.members, reload, refresh };
}
