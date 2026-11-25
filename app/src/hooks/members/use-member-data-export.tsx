import { useCallback, useEffect, useState } from 'react';
import { membersApi } from '@/lib/api';
import type { MemberDataExport, MemberManagementApi } from '@/lib/api/members-api';

export type MemberDataExportStatus = 'loading' | 'ready' | 'error';

type State = { status: MemberDataExportStatus; data: MemberDataExport | null };

const FAILED: State = { status: 'error', data: null };

export function useMemberDataExport(memberId: string, api: Pick<MemberManagementApi, 'exportData'> = membersApi) {
  const [state, setState] = useState<State>({ status: 'loading', data: null });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    api
      .exportData(memberId)
      .then((result) => (result.ok ? ({ status: 'ready', data: result.data } as State) : FAILED), () => FAILED)
      .then((next) => {
        if (active) {
          setState(next);
        }
      });
    return () => {
      active = false;
    };
  }, [api, memberId, attempt]);

  const reload = useCallback(() => {
    setState({ status: 'loading', data: null });
    setAttempt((current) => current + 1);
  }, []);

  return { status: state.status, data: state.data, reload };
}
