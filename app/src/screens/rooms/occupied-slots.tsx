import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import type { RoomOccupancyStatus } from '@/hooks/rooms/use-room-occupancy';
import { bookingsText } from '@/i18n/es/bookings';
import type { OccupiedSlot } from '@/lib/api/rooms-api';

type Props = {
  status: RoomOccupancyStatus;
  occupied: OccupiedSlot[];
  onReload: () => void;
};

export function OccupiedSlots({ status, occupied, onReload }: Props) {
  const text = bookingsText.occupancy;

  if (status === 'idle') {
    return null;
  }

  if (status === 'loading') {
    return <Text role="status">{text.loading}</Text>;
  }

  if (status === 'error') {
    return (
      <View className="gap-3">
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${text.loadError}`}
        </Text>
        <Button variant="outline" accessibilityLabel={text.retry} onPress={onReload}>
          <Text>{text.retry}</Text>
        </Button>
      </View>
    );
  }

  return (
    <View className="gap-3">
      {occupied.length === 0 ? (
        <Text>{text.empty}</Text>
      ) : (
        <View role="list" accessibilityLabel={text.listLabel} className="gap-3">
          {occupied.map((slot) => (
            <View
              key={`${slot.startTime}-${slot.endTime}`}
              role="listitem"
              accessible
              accessibilityLabel={text.slotLabel(slot)}
              className="gap-1 rounded-md border border-border p-3"
            >
              <Text variant="strong">{text.slotText(slot)}</Text>
              <Text>{bookingsText.status[slot.status]}</Text>
              <Text>{slot.ownCommunity ? text.ownCommunity : text.otherCommunity}</Text>
            </View>
          ))}
        </View>
      )}
      <Button variant="outline" accessibilityLabel={text.refresh} onPress={onReload}>
        <Text>{text.refresh}</Text>
      </Button>
    </View>
  );
}
