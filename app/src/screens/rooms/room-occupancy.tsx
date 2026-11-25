import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { useRoomOccupancy } from '@/hooks/rooms/use-room-occupancy';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { bookingsText } from '@/i18n/es/bookings';
import { isDate } from '@/lib/bookings/validation';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { RoomsApi } from '@/lib/api/rooms-api';
import { CommunityPicker } from '@/screens/users/community-picker';
import { OccupiedSlots } from './occupied-slots';

type Props = {
  roomsApi?: RoomsApi;
  communitiesApi?: CommunitiesApi;
};

export function RoomOccupancyScreen({ roomsApi, communitiesApi }: Props) {
  const { user, isModerator, canChooseCommunity } = useCurrentUser();
  const { status: roomsStatus, rooms, reload: reloadRooms } = useRooms(roomsApi, isModerator);
  const [roomId, setRoomId] = useState<string | null>(null);
  const [communityId, setCommunityId] = useState<string | null>(null);
  const [date, setDate] = useState('');
  const day = date.trim();
  const needsCommunity = canChooseCommunity && communityId === null;
  const ready = roomId !== null && isDate(day) && !needsCommunity;
  const { status, occupied, reload } = useRoomOccupancy(
    roomsApi,
    ready ? { roomId, date: day, ...(communityId ? { communityId } : {}) } : null,
  );
  useReloadOnFocus(reload);

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="status">{bookingsText.occupancy.loading}</Text>
      </View>
    );
  }

  if (!isModerator) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${bookingsText.forbidden}`}
        </Text>
      </View>
    );
  }

  const hint =
    roomId !== null && isDate(day) && needsCommunity
      ? bookingsText.occupancy.chooseCommunity
      : bookingsText.occupancy.choose;

  return (
    <ScrollView
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-6"
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
    >
      <Text variant="title">{bookingsText.occupancy.title}</Text>
      <View role="radiogroup" accessibilityLabel={bookingsText.request.room} className="gap-3">
        <Text variant="heading">{bookingsText.request.room}</Text>
        {roomsStatus === 'loading' ? <Text role="status">{bookingsText.request.loadingRooms}</Text> : null}
        {roomsStatus === 'error' ? (
          <View className="gap-3">
            <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
              {`⚠ ${bookingsText.request.roomsError}`}
            </Text>
            <Button
              variant="outline"
              accessibilityLabel={bookingsText.request.retry}
              onPress={() => void reloadRooms()}
            >
              <Text>{bookingsText.request.retry}</Text>
            </Button>
          </View>
        ) : null}
        {roomsStatus === 'ready' && rooms.length === 0 ? <Text>{bookingsText.request.noRooms}</Text> : null}
        {rooms.map((room) => {
          const selected = room.id === roomId;
          return (
            <Button
              key={room.id}
              role="radio"
              variant={selected ? 'default' : 'outline'}
              accessibilityLabel={room.name}
              aria-checked={selected}
              accessibilityState={{ checked: selected }}
              onPress={() => setRoomId(room.id)}
            >
              <Text>{selected ? `✓ ${room.name}` : room.name}</Text>
            </Button>
          );
        })}
      </View>
      <CommunityPicker selectedId={communityId} onSelect={setCommunityId} api={communitiesApi} />
      <View className="gap-2">
        <Text variant="strong">{bookingsText.occupancy.date}</Text>
        <Input
          accessibilityLabel={bookingsText.occupancy.date}
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={date}
          onChangeText={setDate}
        />
      </View>
      {ready ? (
        <OccupiedSlots status={status} occupied={occupied} onReload={() => void reload()} />
      ) : (
        <Text>{hint}</Text>
      )}
    </ScrollView>
  );
}
