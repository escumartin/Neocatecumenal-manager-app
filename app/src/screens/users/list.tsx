import { useState } from 'react';
import { FlatList, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { useUsers } from '@/hooks/users/use-users';
import { messageForUsersFailure, usersText } from '@/i18n/es/users';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { User, UsersApi } from '@/lib/api/users-api';
import { CommunityPicker } from './community-picker';
import { DeleteConfirmation } from './delete-confirmation';

type Props = {
  api?: UsersApi;
  communitiesApi?: CommunitiesApi;
  onCreate: () => void;
  onEdit: (userId: string) => void;
};

export function UserList({ api, communitiesApi, onCreate, onEdit }: Props) {
  const { user: me } = useCurrentUser();
  const [chosenCommunityId, setChosenCommunityId] = useState<string | null>(null);
  const communityId = chosenCommunityId ?? me?.communityId ?? null;
  const { status, users, reload, remove } = useUsers(communityId, api, communityId !== null);
  useReloadOnFocus(reload);
  const [pendingDeletion, setPendingDeletion] = useState<User | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function confirmDeletion() {
    if (!pendingDeletion) {
      return;
    }
    setDeleting(true);
    const result = await remove(pendingDeletion.id);
    setDeleting(false);
    setPendingDeletion(null);
    setMessage(result.ok ? null : messageForUsersFailure(result));
  }

  const header = (
    <View className="gap-4">
      <Text variant="title">{usersText.title}</Text>
      <CommunityPicker selectedId={communityId} onSelect={setChosenCommunityId} api={communitiesApi} />
      <Button accessibilityLabel={usersText.create} onPress={onCreate}>
        <Text>{usersText.create}</Text>
      </Button>
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      {status === 'loading' ? (
        <Text role="status" variant="muted">
          {usersText.loading}
        </Text>
      ) : null}
      {status === 'error' ? (
        <View className="gap-3">
          <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
            {`⚠ ${usersText.loadError}`}
          </Text>
          <Button variant="outline" accessibilityLabel={usersText.retry} onPress={() => void reload()}>
            <Text>{usersText.retry}</Text>
          </Button>
        </View>
      ) : null}
    </View>
  );

  return (
    <>
      <FlatList
        className="flex-1 bg-background"
        contentContainerClassName="gap-4 p-6"
        contentInsetAdjustmentBehavior="automatic"
        keyboardShouldPersistTaps="handled"
        data={users}
        keyExtractor={(user) => user.id}
        ListHeaderComponent={header}
        ListEmptyComponent={status === 'ready' ? <Text variant="muted">{usersText.empty}</Text> : undefined}
        renderItem={({ item: user }) => (
          <View className="gap-2 rounded-md border-2 border-input p-4">
            <Text variant="heading">{user.name}</Text>
            <Text variant="muted">{usersText.accessId(user.accessId)}</Text>
            <Text variant="muted">{usersText.roles[user.role]}</Text>
            <Button
              variant="outline"
              accessibilityLabel={usersText.editFor(user.name)}
              onPress={() => onEdit(user.id)}
            >
              <Text>{usersText.edit}</Text>
            </Button>
            <Button
              variant="outline"
              accessibilityLabel={usersText.deleteFor(user.name)}
              onPress={() => setPendingDeletion(user)}
            >
              <Text>{usersText.remove}</Text>
            </Button>
          </View>
        )}
      />
      {pendingDeletion ? (
        <DeleteConfirmation
          name={pendingDeletion.name}
          busy={deleting}
          onConfirm={() => void confirmDeletion()}
          onCancel={() => setPendingDeletion(null)}
        />
      ) : null}
    </>
  );
}
