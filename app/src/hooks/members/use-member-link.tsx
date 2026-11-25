import { useCallback } from 'react';
import { membersApi } from '@/lib/api';
import type { MemberManagementApi } from '@/lib/api/members-api';

export function useMemberLink(memberId: string, api: Pick<MemberManagementApi, 'link' | 'unlink'> = membersApi) {
  const link = useCallback((userId: string) => api.link(memberId, userId), [api, memberId]);
  const unlink = useCallback(() => api.unlink(memberId), [api, memberId]);
  return { link, unlink };
}
