import { useState } from 'react';
import { RefreshControl, ScrollView, View } from 'react-native';
import { DateField } from '@/components/ui/date-field';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
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
  const { refreshing, onRefresh } = usePullToRefresh(reload);

  if (user === null) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="loading" message={bookingsText.occupancy.loading} />
      </View>
    );
  }

  if (!isModerator) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="error" message={bookingsText.forbidden} />
      </View>
    );
  }

  const hint =
    roomId !== null && isDate(day) && needsCommunity
      ? bookingsText.occupancy.chooseCommunity
      : bookingsText.occupancy.choose;

  return (
    <ScrollView
      testID="occupancy-scroll"
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-4"
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
    >
      <View role="radiogroup" accessibilityLabel={bookingsText.request.room} className="gap-3">
        {roomsStatus === 'loading' ? <StateView kind="loading" message={bookingsText.request.loadingRooms} /> : null}
        {roomsStatus === 'error' ? (
          <StateView
            kind="error"
            message={bookingsText.request.roomsError}
            retryLabel={bookingsText.request.retry}
            onRetry={() => void reloadRooms()}
          />
        ) : null}
        {roomsStatus === 'ready' && rooms.length === 0 ? (
          <StateView kind="empty" icon="door.left.hand.open" message={bookingsText.request.noRooms} />
        ) : null}
        {rooms.length > 0 ? (
          <ListSection title={bookingsText.request.room}>
            {rooms.map((room) => (
              <ListRow
                key={room.id}
                role="radio"
                title={room.name}
                selected={room.id === roomId}
                onPress={() => setRoomId(room.id)}
              />
            ))}
          </ListSection>
        ) : null}
      </View>
      <CommunityPicker selectedId={communityId} onSelect={setCommunityId} api={communitiesApi} />
      <View className="gap-2">
        <Text variant="strong">{bookingsText.occupancy.date}</Text>
        <DateField label={bookingsText.occupancy.date} mode="date" value={date} onChange={setDate} />
      </View>
      {ready ? (
        <OccupiedSlots status={status} occupied={occupied} onReload={() => void reload()} />
      ) : (
        <Text>{hint}</Text>
      )}
    </ScrollView>
  );
}
