import { FlatList, RefreshControl, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { ListRow, rowPosition } from '@/components/ui/list-row';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useCommunities } from '@/hooks/communities/use-communities';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { communitiesText } from '@/i18n/es/communities';
import type { CommunitiesApi, Community } from '@/lib/api/communities-api';

type Props = {
  api?: CommunitiesApi;
  onCreate: () => void;
  onOpen: (communityId: string) => void;
};

function subtitleOf(community: Community) {
  const parts = [community.name, community.status === 'DEACTIVATED' ? communitiesText.list.deactivated : undefined];
  const text = parts.filter(Boolean).join(' · ');
  return text.length > 0 ? text : undefined;
}

export function CommunitiesList({ api, onCreate, onOpen }: Props) {
  const { status, communities, reload, refresh } = useCommunities(api, true, { includeDeactivated: true });
  useReloadOnFocus(refresh);
  const { refreshing, onRefresh } = usePullToRefresh(reload);

  const header = (
    <View className="gap-4 pb-4">
      <Button accessibilityLabel={communitiesText.list.create} onPress={onCreate}>
        <Text>{communitiesText.list.create}</Text>
      </Button>
      {status === 'loading' && !refreshing ? (
        <StateView kind="loading" message={communitiesText.list.loading} />
      ) : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={communitiesText.list.loadError}
          retryLabel={communitiesText.retry}
          onRetry={() => void reload()}
        />
      ) : null}
    </View>
  );

  return (
    <FlatList
      testID="community-list"
      className="flex-1 bg-background"
      contentContainerClassName="p-4"
      contentInsetAdjustmentBehavior="automatic"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
      data={communities}
      keyExtractor={(community) => community.id}
      ListHeaderComponent={header}
      ListEmptyComponent={
        status === 'ready' ? <StateView kind="empty" icon="person.2" message={communitiesText.list.empty} /> : undefined
      }
      renderItem={({ item: community, index }) => (
        <ListRow
          title={communitiesText.option(community.number)}
          subtitle={subtitleOf(community)}
          trailing={community.hasResponsible === false ? communitiesText.list.noResponsible : undefined}
          position={rowPosition(index, communities.length)}
          onPress={() => onOpen(community.id)}
        />
      )}
    />
  );
}
