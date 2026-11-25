import { useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { ListRow, rowPosition } from '@/components/ui/list-row';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useMembers } from '@/hooks/members/use-members';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { membersText } from '@/i18n/es/members';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { MembersApi } from '@/lib/api/members-api';
import { CommunityPicker } from '@/screens/users/community-picker';
import { fullNameOf } from './member-name';

type Props = {
  api?: MembersApi;
  communitiesApi?: CommunitiesApi;
  onCreate: (communityId?: string) => void;
  onOpen: (memberId: string) => void;
};

export function MembersList({ api, communitiesApi, onCreate, onOpen }: Props) {
  const { status: sessionStatus, canChooseCommunity } = useCurrentUser();
  const [chosen, setChosen] = useState<string | null>(null);
  const communityId = canChooseCommunity ? (chosen ?? undefined) : undefined;
  const needsChoice = canChooseCommunity && chosen === null;
  const signedIn = sessionStatus === 'authenticated';
  const { status, members, reload, refresh } = useMembers(communityId, api, signedIn && !needsChoice);
  useReloadOnFocus(refresh);
  const { refreshing, onRefresh } = usePullToRefresh(reload);

  const header = (
    <View className="gap-4 pb-4">
      {canChooseCommunity ? <CommunityPicker selectedId={chosen} onSelect={setChosen} api={communitiesApi} /> : null}
      {needsChoice ? <Text>{membersText.list.chooseCommunity}</Text> : null}
      {signedIn && !needsChoice ? (
        <Button accessibilityLabel={membersText.list.create} onPress={() => onCreate(communityId)}>
          <Text>{membersText.list.create}</Text>
        </Button>
      ) : null}
      {(!signedIn || status === 'loading') && !needsChoice && !refreshing ? (
        <StateView kind="loading" message={membersText.list.loading} />
      ) : null}
      {status === 'deactivated' ? (
        <StateView kind="empty" icon="person.3" message={membersText.list.deactivated} />
      ) : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={membersText.list.loadError}
          retryLabel={membersText.retry}
          onRetry={() => void reload()}
        />
      ) : null}
    </View>
  );

  return (
    <FlatList
      testID="member-list"
      className="flex-1 bg-background"
      contentContainerClassName="p-4"
      contentInsetAdjustmentBehavior="automatic"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
      data={members}
      keyExtractor={(member) => member.id}
      ListHeaderComponent={header}
      ListEmptyComponent={
        status === 'ready' ? <StateView kind="empty" icon="person.2" message={membersText.list.empty} /> : undefined
      }
      renderItem={({ item: member, index }) => (
        <ListRow
          title={fullNameOf(member)}
          subtitle={member.mobile}
          trailing={member.isResponsible ? membersText.list.responsible : undefined}
          position={rowPosition(index, members.length)}
          onPress={() => onOpen(member.id)}
        />
      )}
    />
  );
}
