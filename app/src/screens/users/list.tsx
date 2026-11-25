import { useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { ListRow, rowPosition } from '@/components/ui/list-row';
import { ListSectionFooter } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { useUsers } from '@/hooks/users/use-users';
import { messageForUsersFailure, usersText } from '@/i18n/es/users';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { User, UsersApi } from '@/lib/api/users-api';
import { confirmDestructive } from '@/lib/confirm';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { canManageAccount } from '@/lib/users/can-manage-account';
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
  const { status, users, reload, refresh, remove } = useUsers(communityId, api, communityId !== null);
  useReloadOnFocus(refresh);
  const { refreshing, onRefresh } = usePullToRefresh(reload);
  const [pendingDeletion, setPendingDeletion] = useState<User | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function deleteUser(user: User) {
    try {
      const result = await remove(user.id);
      setMessage(result.ok ? null : messageForUsersFailure(result));
      if (result.ok) {
        hapticSuccess();
      } else {
        hapticFailure();
      }
    } catch {
      hapticFailure();
      setMessage(usersText.errors.UNKNOWN);
    }
  }

  async function confirmDeletion() {
    if (!pendingDeletion) {
      return;
    }
    setDeleting(true);
    await deleteUser(pendingDeletion);
    setDeleting(false);
    setPendingDeletion(null);
  }

  function requestDeletion(user: User) {
    const shownNatively = confirmDestructive({
      title: usersText.delete.title(user.name),
      message: usersText.delete.warning,
      confirmLabel: usersText.delete.confirm,
      cancelLabel: usersText.delete.cancel,
      onConfirm: () => void deleteUser(user),
    });
    if (!shownNatively) {
      setPendingDeletion(user);
    }
  }

  const header = (
    <View className="gap-4 pb-4">
      <CommunityPicker selectedId={communityId} onSelect={setChosenCommunityId} api={communitiesApi} />
      <Button accessibilityLabel={usersText.create} onPress={onCreate}>
        <Text>{usersText.create}</Text>
      </Button>
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      {status === 'loading' && !refreshing ? <StateView kind="loading" message={usersText.loading} /> : null}
      {status === 'error' ? (
        <StateView kind="error" message={usersText.loadError} retryLabel={usersText.retry} onRetry={() => void reload()} />
      ) : null}
    </View>
  );

  return (
    <>
      <FlatList
        testID="user-list"
        className="flex-1 bg-background"
        contentContainerClassName="p-4"
        contentInsetAdjustmentBehavior="automatic"
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
        data={users}
        keyExtractor={(user) => user.id}
        ListHeaderComponent={header}
        ListEmptyComponent={
          status === 'ready' ? <StateView kind="empty" icon="person.2" message={usersText.empty} /> : undefined
        }
        ListFooterComponent={
          users.length > 0 ? <ListSectionFooter className="pt-2">{usersText.deleteTip}</ListSectionFooter> : undefined
        }
        renderItem={({ item: user, index }) => {
          const deletable = canManageAccount(me, user);
          return (
            <ListRow
              title={user.name}
              subtitle={usersText.accessId(user.accessId)}
              trailing={usersText.roles[user.role]}
              position={rowPosition(index, users.length)}
              onPress={() => onEdit(user.id)}
              accessibilityHint={deletable ? usersText.deleteHint : undefined}
              onLongPress={deletable ? () => requestDeletion(user) : undefined}
              accessibilityActions={deletable ? [{ name: 'delete', label: usersText.deleteFor(user.name) }] : undefined}
              onAccessibilityAction={
                deletable
                  ? (event) => {
                      if (event.nativeEvent.actionName === 'delete') {
                        requestDeletion(user);
                      }
                    }
                  : undefined
              }
            />
          );
        }}
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
