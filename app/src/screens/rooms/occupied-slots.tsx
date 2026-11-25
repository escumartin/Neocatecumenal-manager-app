import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import type { RoomOccupancyStatus } from '@/hooks/rooms/use-room-occupancy';
import { bookingsText } from '@/i18n/es/bookings';
import type { OccupiedSlot } from '@/lib/api/rooms-api';
import { BookingStatusBadge } from '@/screens/bookings/booking-status-badge';

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
    return <StateView kind="loading" message={text.loading} />;
  }

  if (status === 'error') {
    return (
      <StateView kind="error" message={text.loadError} retryLabel={text.retry} onRetry={onReload} />
    );
  }

  return (
    <View className="gap-3">
      {occupied.length === 0 ? (
        <StateView kind="empty" icon="calendar" message={text.empty} />
      ) : (
        <View role="list" accessibilityLabel={text.listLabel} className="gap-3">
          {occupied.map((slot) => (
            <View
              key={`${slot.startTime}-${slot.endTime}`}
              role="listitem"
              accessible
              accessibilityLabel={text.slotLabel(slot)}
              className="gap-1 rounded-lg border-hairline border-border bg-card p-4"
            >
              <Text variant="strong">{text.slotText(slot)}</Text>
              <BookingStatusBadge status={slot.status} />
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
