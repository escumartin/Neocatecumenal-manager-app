import { useState } from 'react';
import { FlatList, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { messageForRoomsFailure, roomsText } from '@/i18n/es/rooms';
import type { Room, RoomsApi } from '@/lib/api/rooms-api';
import { DeactivateConfirmation } from './deactivate-confirmation';
import { RoomNameForm } from './room-name-form';

type Props = {
  api?: RoomsApi;
};

export function RoomsAdminScreen({ api }: Props) {
  const { user, isAdministrator } = useCurrentUser();
  const { status, rooms, reload, create, rename, deactivate } = useRooms(api, isAdministrator);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [pendingDeactivation, setPendingDeactivation] = useState<Room | null>(null);
  const [deactivating, setDeactivating] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="status">{roomsText.loading}</Text>
      </View>
    );
  }

  if (!isAdministrator) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${roomsText.forbidden}`}
        </Text>
      </View>
    );
  }

  async function confirmDeactivation() {
    if (!pendingDeactivation) {
      return;
    }
    setDeactivating(true);
    try {
      const result = await deactivate(pendingDeactivation.id);
      setMessage(result.ok ? null : messageForRoomsFailure(result));
    } catch {
      setMessage(roomsText.errors.UNKNOWN);
    } finally {
      setDeactivating(false);
      setPendingDeactivation(null);
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
    <View className="gap-4">
      <Text variant="title">{roomsText.title}</Text>
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
      {status === 'loading' ? <Text role="status">{roomsText.loading}</Text> : null}
      {status === 'error' ? (
        <View className="gap-3">
          <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
            {`⚠ ${roomsText.loadError}`}
          </Text>
          <Button variant="outline" accessibilityLabel={roomsText.retry} onPress={() => void reload()}>
            <Text>{roomsText.retry}</Text>
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
        data={rooms}
        keyExtractor={(room) => room.id}
        ListHeaderComponent={header}
        ListEmptyComponent={status === 'ready' ? <Text>{roomsText.empty}</Text> : undefined}
        renderItem={({ item: room }) =>
          editingId === room.id ? (
            <View className="gap-2 rounded-md border-2 border-input p-4">
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
          ) : (
            <View className="gap-2 rounded-md border-2 border-input p-4">
              <Text variant="heading">{room.name}</Text>
              <Button
                variant="outline"
                accessibilityLabel={roomsText.rename.actionFor(room.name)}
                onPress={() => setEditingId(room.id)}
              >
                <Text>{roomsText.rename.action}</Text>
              </Button>
              <Button
                variant="outline"
                accessibilityLabel={roomsText.deactivate.actionFor(room.name)}
                onPress={() => setPendingDeactivation(room)}
              >
                <Text>{roomsText.deactivate.action}</Text>
              </Button>
            </View>
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
