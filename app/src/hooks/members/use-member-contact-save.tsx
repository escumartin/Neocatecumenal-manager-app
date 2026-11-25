import { useCallback } from 'react';
import { membersApi } from '@/lib/api';
import type { MemberContactInput, MemberManagementApi } from '@/lib/api/members-api';

export function useMemberContactSave(memberId: string, api: MemberManagementApi = membersApi) {
  const save = useCallback((input: MemberContactInput) => api.changeContact(memberId, input), [api, memberId]);
  return { save };
}
