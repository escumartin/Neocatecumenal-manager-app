import { useCallback, useEffect, useState } from 'react';
import { myMemberApi } from '@/lib/api';
import type { ContactInput, MyMember, MyMemberApi, MyMemberResult } from '@/lib/api/members-api';

export type OwnMemberStatus = 'loading' | 'ready' | 'not-linked' | 'error';

type State = { status: OwnMemberStatus; myMember: MyMember | null };

const FAILED: State = { status: 'error', myMember: null };

function stateOf(result: MyMemberResult): State {
  if (result.ok) {
    return { status: 'ready', myMember: result.myMember };
  }
  return result.code === 'MEMBER_NOT_LINKED' ? { status: 'not-linked', myMember: null } : FAILED;
}

export function useOwnMember(api: MyMemberApi = myMemberApi) {
  const [state, setState] = useState<State>({ status: 'loading', myMember: null });

  useEffect(() => {
    let active = true;
    api
      .get()
      .then(stateOf, () => FAILED)
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api]);

  const reload = useCallback(async () => {
    setState({ status: 'loading', myMember: null });
    try {
      setState(stateOf(await api.get()));
    } catch {
      setState(FAILED);
    }
  }, [api]);

  const save = useCallback(
    async (input: ContactInput) => {
      const result = await api.changeContact(input);
      if (result.ok) {
        setState({ status: 'ready', myMember: result.myMember });
      }
      return result;
    },
    [api],
  );

  return { status: state.status, myMember: state.myMember, reload, save };
}
