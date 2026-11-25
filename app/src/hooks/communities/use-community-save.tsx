import { useCallback } from 'react';
import { communitiesApi } from '@/lib/api';
import type { CommunityInput, CommunityManagementApi } from '@/lib/api/communities-api';

export function useCommunitySave(communityId: string | undefined, api: CommunityManagementApi = communitiesApi) {
  const save = useCallback(
    (input: CommunityInput) => (communityId ? api.update(communityId, input) : api.create(input)),
    [api, communityId],
  );
  return { save };
}
