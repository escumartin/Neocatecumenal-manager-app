import { useCallback } from 'react';
import { membersApi } from '@/lib/api';
import type { MemberManagementApi } from '@/lib/api/members-api';

export function useMemberTransfer(memberId: string, api: Pick<MemberManagementApi, 'transfer'> = membersApi) {
  const transfer = useCallback((communityId: string) => api.transfer(memberId, communityId), [api, memberId]);
  return { transfer };
}
