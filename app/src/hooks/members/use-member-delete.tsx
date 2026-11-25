import { useCallback } from 'react';
import { membersApi } from '@/lib/api';
import type { MemberManagementApi } from '@/lib/api/members-api';

export function useMemberDelete(memberId: string, api: Pick<MemberManagementApi, 'remove'> = membersApi) {
  const remove = useCallback(() => api.remove(memberId), [api, memberId]);
  return { remove };
}
