import type { StatusTone } from '@/components/ui/status-badge';
import type { BookingStatusName } from '@/i18n/es/bookings';

const tones: Record<BookingStatusName, StatusTone> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'destructive',
  CANCELLED: 'neutral',
};

export function toneForBookingStatus(status: BookingStatusName): StatusTone {
  return tones[status];
}
