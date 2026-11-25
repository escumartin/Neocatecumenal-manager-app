import { useCallback, useEffect, useState } from 'react';
import { membersApi } from '@/lib/api';
import type { Member, MemberManagementApi, MemberResult } from '@/lib/api/members-api';

export type MemberStatus = 'loading' | 'ready' | 'unavailable' | 'error';

type State = { status: MemberStatus; member: Member | null };

const FAILED: State = { status: 'error', member: null };
const UNAVAILABLE: State = { status: 'unavailable', member: null };

function toState(result: MemberResult): State {
  if (result.ok) {
    return { status: 'ready', member: result.member };
  }
  return result.status === 403 || result.status === 404 ? UNAVAILABLE : FAILED;
}

export function useMember(memberId: string, api: Pick<MemberManagementApi, 'get'> = membersApi) {
  const [state, setState] = useState<State>({ status: 'loading', member: null });

  useEffect(() => {
    let active = true;
    api
      .get(memberId)
      .then(toState, () => FAILED)
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
      setState(toState(result));
    } catch {
      setState(FAILED);
    }
  }, [api, memberId]);

  const refresh = useCallback(async () => {
    try {
      const result = await api.get(memberId);
      if (result.ok) {
        setState({ status: 'ready', member: result.member });
      }
    } catch {
      return;
    }
  }, [api, memberId]);

  return { status: state.status, member: state.member, reload, refresh };
}
