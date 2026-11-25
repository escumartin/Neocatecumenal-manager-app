import { ScrollView, View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { useEvent } from '@/hooks/events/use-event';
import { useRooms } from '@/hooks/rooms/use-rooms';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { bookingsText } from '@/i18n/es/bookings';
import { eventsText } from '@/i18n/es/events';
import { eventsApi } from '@/lib/api';
import { resolveRoomName } from '@/lib/rooms/resolve-room-name';
import type { EventsApi } from '@/lib/api/events-api';
import type { RoomsApi } from '@/lib/api/rooms-api';

type Props = {
  eventId: string;
  api?: EventsApi;
  roomsApi?: RoomsApi;
};

function Problem({ message }: { message: string }) {
  return (
    <View className="flex-1 bg-background p-6">
      <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
        {`⚠ ${message}`}
      </Text>
    </View>
  );
}

export function EventScreen({ eventId, api = eventsApi, roomsApi }: Props) {
  const { user, isModerator } = useCurrentUser();
  const { status, event, reload } = useEvent(eventId, api, isModerator);
  const { status: roomsStatus, rooms } = useRooms(roomsApi, isModerator);

  if (user === null || status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <Text role="status">{eventsText.detail.loading}</Text>
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
      <View className="flex-1 gap-3 bg-background p-6">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${eventsText.detail.loadError}`}
        </Text>
        <Button variant="outline" accessibilityLabel={eventsText.detail.retry} onPress={() => void reload()}>
          <Text>{eventsText.detail.retry}</Text>
        </Button>
      </View>
    );
  }

  const roomName = resolveRoomName(roomsStatus, new Map(rooms.map((room) => [room.id, room.name])), event.roomId, {
    loading: eventsText.detail.roomLoading,
    unavailable: eventsText.detail.roomUnavailable,
    deactivated: eventsText.detail.unknownRoom,
  });

  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="gap-6 p-6">
      <Text variant="title">{event.title}</Text>
      <View className="gap-1">
        <Text variant="strong">{eventsText.detail.type}</Text>
        <Text>{eventsText.types[event.type]}</Text>
      </View>
      <View className="gap-1">
        <Text variant="strong">{eventsText.detail.room}</Text>
        <Text>{roomName}</Text>
      </View>
      <View className="gap-1">
        <Text variant="strong">{eventsText.detail.whenLabel}</Text>
        <Text>{eventsText.detail.when(event)}</Text>
      </View>
      <View className="gap-1">
        <Text variant="strong">{eventsText.detail.statusLabel}</Text>
        <Text>{bookingsText.status[event.bookingStatus]}</Text>
      </View>
    </ScrollView>
  );
}
