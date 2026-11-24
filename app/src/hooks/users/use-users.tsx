import { useCallback, useEffect, useState } from 'react';
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

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let active = true;
    api.list(communityId ?? undefined).then((result) => {
      if (active) {
        setState(toState(communityId, result));
      }
    });
    return () => {
      active = false;
    };
  }, [api, communityId, enabled]);

  const reload = useCallback(async () => {
    setState((current) => ({ ...current, status: 'loading' }));
    setState(toState(communityId, await api.list(communityId ?? undefined)));
  }, [api, communityId]);

  const create = useCallback((input: UserInput) => api.create(input), [api]);

  const remove = useCallback(
    async (userId: string) => {
      const result = await api.remove(userId);
      if (result.ok) {
        setState((current) => ({ ...current, users: current.users.filter((user) => user.id !== userId) }));
      }
      return result;
    },
    [api],
  );

  const current = state.communityId === communityId;
  const status: UsersStatus = !enabled ? 'idle' : current ? state.status : 'loading';

  return { status, users: current ? state.users : [], reload, create, remove };
}
