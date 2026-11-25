import { useCallback, useEffect, useState } from 'react';
import { membersApi } from '@/lib/api';
import type { Member, MemberManagementApi } from '@/lib/api/members-api';

export type MemberStatus = 'loading' | 'ready' | 'error';

type State = { status: MemberStatus; member: Member | null };

const FAILED: State = { status: 'error', member: null };

export function useMember(memberId: string, api: Pick<MemberManagementApi, 'get'> = membersApi) {
  const [state, setState] = useState<State>({ status: 'loading', member: null });

  useEffect(() => {
    let active = true;
    api
      .get(memberId)
      .then((result) => (result.ok ? ({ status: 'ready', member: result.member } as State) : FAILED), () => FAILED)
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, memberId]);

  const reload = useCallback(async () => {
    setState({ status: 'loading', member: null });
    try {
      const result = await api.get(memberId);
      setState(result.ok ? { status: 'ready', member: result.member } : FAILED);
    } catch {
      setState(FAILED);
    }
  }, [api, memberId]);

  return { status: state.status, member: state.member, reload };
}
