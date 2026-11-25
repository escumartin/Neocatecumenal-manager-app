import { useCallback, useEffect, useRef, useState } from 'react';
import { usersApi } from '@/lib/api';
import type { ListUsersResult, User, UserInput, UsersApi } from '@/lib/api/users-api';

export type UsersStatus = 'idle' | 'loading' | 'ready' | 'error';

type State = { communityId: string | null; status: UsersStatus; users: User[] };

function toState(communityId: string | null, result: ListUsersResult): State {
  return result.ok
    ? { communityId, status: 'ready', users: result.users }
    : { communityId, status: 'error', users: [] };
}

export function useUsers(communityId: string | null, api: UsersApi = usersApi, enabled = true) {
  const [state, setState] = useState<State>({ communityId: null, status: 'idle', users: [] });

  const sequence = useRef(0);
  const communityRef = useRef(communityId);

  useEffect(() => {
    communityRef.current = communityId;
  }, [communityId]);

  useEffect(() => {
    const id = ++sequence.current;
    if (!enabled) {
      return;
    }
    let active = true;
    api.list(communityId ?? undefined).then((result) => {
      if (active && id === sequence.current) {
        setState(toState(communityId, result));
      }
    });
    return () => {
      active = false;
    };
  }, [api, communityId, enabled]);

  const reload = useCallback(async () => {
    const id = ++sequence.current;
    setState((current) => ({ ...current, status: 'loading' }));
    const result = await api.list(communityId ?? undefined);
    if (id === sequence.current && communityRef.current === communityId) {
      setState(toState(communityId, result));
    }
  }, [api, communityId]);

  const refresh = useCallback(async () => {
    if (!enabled) {
      return;
    }
    const id = ++sequence.current;
    try {
      const result = await api.list(communityId ?? undefined);
      if (result.ok && id === sequence.current && communityRef.current === communityId) {
        setState(toState(communityId, result));
      }
    } catch {
      return;
    }
  }, [api, communityId, enabled]);

  const create = useCallback((input: UserInput) => api.create(input), [api]);

  const remove = useCallback(
    async (userId: string) => {
      const result = await api.remove(userId);
      if (result.ok) {
        sequence.current++;
        setState((current) => ({ ...current, users: current.users.filter((user) => user.id !== userId) }));
      }
      return result;
    },
    [api],
  );

  const current = state.communityId === communityId;
  const status: UsersStatus = !enabled ? 'idle' : current ? state.status : 'loading';

  return { status, users: current ? state.users : [], reload, refresh, create, remove };
}
