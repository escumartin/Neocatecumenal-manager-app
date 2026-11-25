import { useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { ListRow, rowPosition } from '@/components/ui/list-row';
import { ListSection, ListSectionFooter } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { messageForRoomsFailure, roomsText } from '@/i18n/es/rooms';
import type { Room, RoomsApi } from '@/lib/api/rooms-api';
import { confirmDestructive } from '@/lib/confirm';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { DeactivateConfirmation } from './deactivate-confirmation';
import { RoomNameForm } from './room-name-form';

type Props = {
  api?: RoomsApi;
};

export function RoomsAdminScreen({ api }: Props) {
  const { user, isAdministrator } = useCurrentUser();
  const { status, rooms, reload, create, rename, deactivate } = useRooms(api, isAdministrator);
  const { refreshing, onRefresh } = usePullToRefresh(reload);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [pendingDeactivation, setPendingDeactivation] = useState<Room | null>(null);
  const [deactivating, setDeactivating] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="loading" message={roomsText.loading} />
      </View>
    );
  }

  if (!isAdministrator) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="error" message={roomsText.forbidden} />
      </View>
    );
  }

  async function deactivateRoom(room: Room) {
    try {
      const result = await deactivate(room.id);
      setMessage(result.ok ? null : messageForRoomsFailure(result));
      if (result.ok) {
        hapticSuccess();
      } else {
        hapticFailure();
      }
    } catch {
      hapticFailure();
      setMessage(roomsText.errors.UNKNOWN);
    }
  }

  async function confirmDeactivation() {
    if (!pendingDeactivation) {
      return;
    }
    setDeactivating(true);
    await deactivateRoom(pendingDeactivation);
    setDeactivating(false);
    setPendingDeactivation(null);
  }

  function requestDeactivation(room: Room) {
    const shownNatively = confirmDestructive({
      title: roomsText.deactivate.title(room.name),
      message: roomsText.deactivate.warning,
      confirmLabel: roomsText.deactivate.confirm,
      cancelLabel: roomsText.deactivate.cancel,
      onConfirm: () => void deactivateRoom(room),
    });
    if (!shownNatively) {
      setPendingDeactivation(room);
    }
  }

  async function submitRename(room: Room, name: string) {
    const result = await rename(room.id, name);
    if (result.ok) {
      setEditingId(null);
    }
    return result;
  }

  const header = (
    <View className="gap-4 pb-4">
      <RoomNameForm
        label={roomsText.add.name}
        submitLabel={roomsText.add.submit}
        submittingLabel={roomsText.add.submitting}
        clearOnSuccess
        onSubmit={create}
      />
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      {status === 'loading' && !refreshing ? <StateView kind="loading" message={roomsText.loading} /> : null}
      {status === 'error' ? (
        <StateView kind="error" message={roomsText.loadError} retryLabel={roomsText.retry} onRetry={() => void reload()} />
      ) : null}
    </View>
  );

  return (
    <>
      <FlatList
        testID="rooms-list"
        className="flex-1 bg-background"
        contentContainerClassName="p-4"
        contentInsetAdjustmentBehavior="automatic"
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
        data={rooms}
        keyExtractor={(room) => room.id}
        ListHeaderComponent={header}
        ListEmptyComponent={
          status === 'ready' ? (
            <StateView kind="empty" icon="door.left.hand.open" message={roomsText.empty} />
          ) : undefined
        }
        ListFooterComponent={
          rooms.length > 0 ? <ListSectionFooter className="pt-2">{roomsText.deactivateTip}</ListSectionFooter> : undefined
        }
        renderItem={({ item: room, index }) =>
          editingId === room.id ? (
            <View className="my-2 gap-4">
              <View className="rounded-lg border-hairline border-border bg-card p-4">
                <RoomNameForm
                  label={roomsText.rename.name}
                  submitLabel={roomsText.rename.save}
                  submittingLabel={roomsText.rename.saving}
                  initialName={room.name}
                  cancelLabel={roomsText.rename.cancel}
                  onCancel={() => setEditingId(null)}
                  onSubmit={(name) => submitRename(room, name)}
                />
              </View>
              <ListSection>
                <ListRow destructive title={roomsText.deactivate.action} onPress={() => requestDeactivation(room)} />
              </ListSection>
            </View>
          ) : (
            <ListRow
              title={room.name}
              position={rowPosition(index, rooms.length)}
              onPress={() => setEditingId(room.id)}
              accessibilityHint={roomsText.deactivateHint}
              onLongPress={() => requestDeactivation(room)}
              accessibilityActions={[{ name: 'deactivate', label: roomsText.deactivate.actionFor(room.name) }]}
              onAccessibilityAction={(event) => {
                if (event.nativeEvent.actionName === 'deactivate') {
                  requestDeactivation(room);
                }
              }}
            />
          )
        }
      />
      {pendingDeactivation ? (
        <DeactivateConfirmation
          name={pendingDeactivation.name}
          busy={deactivating}
          onConfirm={() => void confirmDeactivation()}
          onCancel={() => setPendingDeactivation(null)}
        />
      ) : null}
    </>
  );
}
