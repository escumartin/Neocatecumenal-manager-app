import { useCallback, useEffect, useState } from 'react';
import { communitiesApi, membersApi as defaultMembersApi } from '@/lib/api';
import type { CommunityDetail, CommunityManagementApi, CommunityResult } from '@/lib/api/communities-api';
import type { Member, MembersApi } from '@/lib/api/members-api';

export type ResponsibleStatus = 'loading' | 'ready' | 'error';

type State = {
  status: ResponsibleStatus;
  community: CommunityDetail | null;
  members: Member[];
};

const LOADING: State = { status: 'loading', community: null, members: [] };
const FAILED: State = { status: 'error', community: null, members: [] };

async function fetchState(communityId: string, api: CommunityManagementApi, membersApi: MembersApi): Promise<State> {
  try {
    const [community, members] = await Promise.all([api.get(communityId), membersApi.list(communityId)]);
    if (!community.ok || !members.ok) {
      return FAILED;
    }
    return {
      status: 'ready',
      community: community.community,
      members: members.members.filter((member) => member.communityId === communityId),
    };
  } catch {
    return FAILED;
  }
}

export function useCommunityResponsible(
  communityId: string,
  api: CommunityManagementApi = communitiesApi,
  membersApi: MembersApi = defaultMembersApi,
) {
  const [state, setState] = useState<State>(LOADING);

  useEffect(() => {
    let active = true;
    fetchState(communityId, api, membersApi).then((next) => {
      if (active) {
        setState(next);
      }
    });
    return () => {
      active = false;
    };
  }, [api, communityId, membersApi]);

  const reload = useCallback(async () => {
    setState(LOADING);
    setState(await fetchState(communityId, api, membersApi));
  }, [api, communityId, membersApi]);

  const designate = useCallback(
    async (memberId: string): Promise<CommunityResult> => {
      const result = await api.designateResponsible(communityId, memberId);
      if (result.ok) {
        setState((current) => ({ ...current, community: result.community }));
      }
      return result;
    },
    [api, communityId],
  );

  return {
    status: state.status,
    community: state.community,
    members: state.members,
    reload,
    designate,
  };
}
