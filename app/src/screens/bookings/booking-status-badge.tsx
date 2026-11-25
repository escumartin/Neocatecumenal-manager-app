import { StatusBadge } from '@/components/ui/status-badge';
import { bookingsText, type BookingStatusName } from '@/i18n/es/bookings';
import { toneForBookingStatus } from '@/lib/bookings/status-tone';

export function BookingStatusBadge({ status }: { status: BookingStatusName }) {
  return <StatusBadge tone={toneForBookingStatus(status)} label={bookingsText.status[status]} />;
}
