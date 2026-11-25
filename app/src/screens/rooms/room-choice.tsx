import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { RadioSection } from '@/components/ui/radio-section';
import { Text } from '@/components/ui/text';
import type { RoomsStatus } from '@/hooks/rooms/use-rooms';
import type { Room } from '@/lib/api/rooms-api';

type RoomChoiceText = {
  loadingRooms: string;
  roomsError: string;
  retry: string;
  noRooms: string;
};

type Props = {
  title: string;
  text: RoomChoiceText;
  status: RoomsStatus;
  rooms: Room[];
  selectedId: string | null;
  onSelect: (roomId: string) => void;
  onRetry: () => void;
};

export function RoomChoice({ title, text, status, rooms, selectedId, onSelect, onRetry }: Props) {
  return (
    <View className="gap-3">
      {rooms.length > 0 ? (
        <RadioSection
          title={title}
          options={rooms.map((room) => ({ value: room.id, label: room.name }))}
          value={selectedId}
          onChange={onSelect}
        />
      ) : (
        <Text variant="heading">{title}</Text>
      )}
      {status === 'loading' ? <Text role="status">{text.loadingRooms}</Text> : null}
      {status === 'error' ? (
        <View className="gap-3">
          <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
            {`⚠ ${text.roomsError}`}
          </Text>
          <Button variant="outline" accessibilityLabel={text.retry} onPress={onRetry}>
            <Text>{text.retry}</Text>
          </Button>
        </View>
      ) : null}
      {status === 'ready' && rooms.length === 0 ? <Text>{text.noRooms}</Text> : null}
    </View>
  );
}
