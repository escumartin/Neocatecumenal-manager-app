import { useMemo, useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { ListRow, rowPosition } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useMembers } from '@/hooks/members/use-members';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { membersText } from '@/i18n/es/members';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { MembersApi } from '@/lib/api/members-api';
import { filterMembers } from '@/lib/members/directory-search';
import { fullNameOf } from '@/screens/members/member-name';
import { CommunityPicker } from '@/screens/users/community-picker';

type Props = {
  api?: MembersApi;
  communitiesApi?: CommunitiesApi;
  onOpen: (memberId: string) => void;
};

export function DirectoryScreen({ api, communitiesApi, onOpen }: Props) {
  const { status: sessionStatus, canChooseCommunity } = useCurrentUser();
  const [chosen, setChosen] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const communityId = canChooseCommunity ? (chosen ?? undefined) : undefined;
  const needsChoice = canChooseCommunity && chosen === null;
  const signedIn = sessionStatus === 'authenticated';
  const { status, members, reload, refresh } = useMembers(communityId, api, signedIn && !needsChoice);
  useReloadOnFocus(refresh);
  const { refreshing, onRefresh } = usePullToRefresh(reload);
  const visible = useMemo(() => filterMembers(members, query), [members, query]);

  return (
    <ScrollView
      testID="directory"
      className="flex-1 bg-background"
      contentContainerClassName="gap-4 p-4"
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
    >
      {canChooseCommunity ? <CommunityPicker selectedId={chosen} onSelect={setChosen} api={communitiesApi} /> : null}
      {needsChoice ? <Text>{membersText.directory.chooseCommunity}</Text> : null}
      {(!signedIn || status === 'loading') && !needsChoice && !refreshing ? (
        <StateView kind="loading" message={membersText.directory.loading} />
      ) : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={membersText.directory.loadError}
          retryLabel={membersText.retry}
          onRetry={() => void reload()}
        />
      ) : null}
      {status === 'deactivated' ? (
        <StateView kind="empty" icon="person.3" message={membersText.directory.deactivated} />
      ) : null}
      {status === 'ready' && members.length === 0 ? (
        <StateView kind="empty" icon="person.2" message={membersText.directory.empty} />
      ) : null}
      {status === 'ready' && members.length > 0 ? (
        <>
          <ListSection>
            <FormField label={membersText.directory.searchLabel} description={membersText.directory.searchHint}>
              <Input
                accessibilityLabel={membersText.directory.searchLabel}
                autoCapitalize="none"
                autoCorrect={false}
                returnKeyType="search"
                value={query}
                onChangeText={setQuery}
              />
            </FormField>
          </ListSection>
          {visible.length === 0 ? (
            <View className="gap-3">
              <StateView kind="empty" icon="person.2" message={membersText.directory.noResults} />
              <Button
                variant="outline"
                accessibilityLabel={membersText.directory.clearSearch}
                onPress={() => setQuery('')}
              >
                <Text>{membersText.directory.clearSearch}</Text>
              </Button>
            </View>
          ) : (
            <ListSection>
              {visible.map((member, index) => (
                <ListRow
                  key={member.id}
                  title={fullNameOf(member)}
                  subtitle={member.mobile}
                  trailing={member.isResponsible ? membersText.list.responsible : undefined}
                  position={rowPosition(index, visible.length)}
                  onPress={() => onOpen(member.id)}
                />
              ))}
            </ListSection>
          )}
        </>
      ) : null}
    </ScrollView>
  );
}
