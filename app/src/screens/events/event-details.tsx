import { RefreshControl, ScrollView, View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useEvent } from '@/hooks/events/use-event';
import { usePullToRefresh } from '@/hooks/navigation/use-pull-to-refresh';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { eventsText } from '@/i18n/es/events';
import { eventsApi } from '@/lib/api';
import { resolveRoomName } from '@/lib/rooms/resolve-room-name';
import type { EventsApi } from '@/lib/api/events-api';
import type { RoomsApi } from '@/lib/api/rooms-api';
import { BookingStatusBadge } from '@/screens/bookings/booking-status-badge';

type Props = {
  eventId: string;
  api?: EventsApi;
  roomsApi?: RoomsApi;
};

function Problem({ message }: { message: string }) {
  return (
    <View className="flex-1 bg-background p-4">
      <StateView kind="error" message={message} />
    </View>
  );
}

export function EventScreen({ eventId, api = eventsApi, roomsApi }: Props) {
  const { user, isModerator } = useCurrentUser();
  const { status, event, reload } = useEvent(eventId, api, isModerator);
  const { status: roomsStatus, rooms } = useRooms(roomsApi, isModerator);
  const { refreshing, onRefresh } = usePullToRefresh(reload);

  if (user === null || (status === 'loading' && event === null)) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView kind="loading" message={eventsText.detail.loading} />
      </View>
    );
  }

  if (!isModerator || status === 'forbidden') {
    return <Problem message={eventsText.detail.forbidden} />;
  }

  if (status === 'notFound') {
    return <Problem message={eventsText.detail.notFound} />;
  }

  if (status === 'error' || event === null) {
    return (
      <View className="flex-1 bg-background p-4">
        <StateView
          kind="error"
          message={eventsText.detail.loadError}
          retryLabel={eventsText.detail.retry}
          onRetry={() => void reload()}
        />
      </View>
    );
  }

  const roomName = resolveRoomName(roomsStatus, new Map(rooms.map((room) => [room.id, room.name])), event.roomId, {
    loading: eventsText.detail.roomLoading,
    unavailable: eventsText.detail.roomUnavailable,
    deactivated: eventsText.detail.unknownRoom,
  });

  return (
    <ScrollView
      testID="event-scroll"
      className="flex-1 bg-background"
      contentContainerClassName="gap-6 p-4"
      contentInsetAdjustmentBehavior="automatic"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} />}
    >
      <Text variant="title">{event.title}</Text>
      <ListSection>
        <ListRow title={eventsText.detail.type} subtitle={eventsText.types[event.type]} />
        <ListRow title={eventsText.detail.room} subtitle={roomName} />
        <ListRow title={eventsText.detail.whenLabel} subtitle={eventsText.detail.when(event)} />
        <ListRow title={eventsText.detail.statusLabel}>
          <BookingStatusBadge status={event.bookingStatus} />
        </ListRow>
      </ListSection>
    </ScrollView>
  );
}
