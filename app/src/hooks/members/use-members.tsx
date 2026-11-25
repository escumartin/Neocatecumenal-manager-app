import { useCallback, useEffect, useRef, useState } from 'react';
import { membersApi } from '@/lib/api';
import type { ListMembersResult, Member, MembersApi } from '@/lib/api/members-api';

export type MembersStatus = 'idle' | 'loading' | 'ready' | 'deactivated' | 'error';

type State = { key: string | null; status: MembersStatus; members: Member[] };

function toState(key: string, result: ListMembersResult): State {
  if (result.ok) {
    return { key, status: 'ready', members: result.members };
  }
  const deactivated = result.status === 409 && result.code === 'COMMUNITY_DEACTIVATED';
  return { key, status: deactivated ? 'deactivated' : 'error', members: [] };
}

export function useMembers(communityId: string | undefined, api: MembersApi = membersApi, enabled = true) {
  const key = communityId ?? '';
  const [state, setState] = useState<State>({ key: null, status: 'idle', members: [] });
  const sequence = useRef(0);
  const current: State =
    state.key === key ? state : { key, status: enabled ? 'loading' : 'idle', members: [] };

  useEffect(() => {
    const id = ++sequence.current;
    if (!enabled) {
      return;
    }
    let active = true;
    api
      .list(communityId)
      .then((result) => toState(key, result), () => toState(key, { ok: false, status: 0 }))
      .then((next) => {
        if (active && id === sequence.current) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, communityId, enabled, key]);

  const reload = useCallback(async () => {
    const id = ++sequence.current;
    setState({ key, status: 'loading', members: [] });
    let next = toState(key, { ok: false, status: 0 });
    try {
      next = toState(key, await api.list(communityId));
    } catch {
      next = toState(key, { ok: false, status: 0 });
    }
    if (id === sequence.current) {
      setState(next);
    }
  }, [api, communityId, key]);

  const refresh = useCallback(async () => {
    if (!enabled) {
      return;
    }
    const id = ++sequence.current;
    try {
      const result = await api.list(communityId);
      if (result.ok && id === sequence.current) {
        setState(toState(key, result));
      }
    } catch {
      return;
    }
  }, [api, communityId, enabled, key]);

  return { status: current.status, members: current.members, reload, refresh };
}
