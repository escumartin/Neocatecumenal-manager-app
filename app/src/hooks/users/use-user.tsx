import { useEffect, useState } from 'react';
import { usersApi } from '@/lib/api';
import type { User, UserInput, UsersApi } from '@/lib/api/users-api';

export type UserStatus = 'loading' | 'ready' | 'error';

export function useUser(userId: string | undefined, api: UsersApi = usersApi) {
  const [state, setState] = useState<{ status: UserStatus; user: User | null }>({
    status: userId ? 'loading' : 'ready',
    user: null,
  });

  useEffect(() => {
    if (!userId) {
      return;
    }
    let active = true;
    api.get(userId).then((result) => {
      if (active) {
        setState(result.ok ? { status: 'ready', user: result.user } : { status: 'error', user: null });
      }
    });
    return () => {
      active = false;
    };
  }, [api, userId]);

  return {
    status: state.status,
    user: state.user,
    save: (input: UserInput): ReturnType<UsersApi['update']> =>
      userId ? api.update(userId, input) : Promise.resolve({ ok: false, status: 404 }),
  };
}
