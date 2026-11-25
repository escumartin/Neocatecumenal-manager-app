import { useCallback } from 'react';
import { membersApi } from '@/lib/api';
import type { MemberInput, MemberManagementApi, MemberNameInput } from '@/lib/api/members-api';

export function useMemberSave(memberId: string | undefined, api: MemberManagementApi = membersApi) {
  const save = useCallback(
    (input: MemberInput) => {
      if (memberId) {
        const name: MemberNameInput = {
          givenName: input.givenName,
          ...(input.surnames ? { surnames: input.surnames } : {}),
        };
        return api.rename(memberId, name);
      }
      return api.create(input);
    },
    [api, memberId],
  );
  return { save };
}
