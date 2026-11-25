import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { bookingsText, messageForResolutionFailure } from '@/i18n/es/bookings';
import type { BookingRequest, BookingRequestResult } from '@/lib/api/booking-requests-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { ReasonField } from './reason-field';

type Props = {
  request: BookingRequest;
  roomName: string;
  communityNumber?: number;
  onApprove: (id: string) => Promise<BookingRequestResult>;
  onReject: (id: string, reason?: string) => Promise<BookingRequestResult>;
};

export function PendingRequestCard({ request, roomName, communityNumber, onApprove, onReject }: Props) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  async function run(action: () => Promise<BookingRequestResult>) {
    setWorking(true);
    setProblem(null);
    try {
      const result = await action();
      if (result.ok) {
        hapticSuccess();
      } else if (result.code !== 'BOOKING_REQUEST_ALREADY_RESOLVED') {
        hapticFailure();
        setProblem(messageForResolutionFailure(result));
      }
    } catch {
      hapticFailure();
      setProblem(bookingsText.errors.UNKNOWN);
    } finally {
      setWorking(false);
    }
  }

  function confirmRejection() {
    const trimmed = reason.trim();
    return run(() => onReject(request.id, trimmed.length > 0 ? trimmed : undefined));
  }

  return (
    <View className="gap-2 rounded-lg border-hairline border-border bg-card p-4">
      <Text variant="heading">{roomName}</Text>
      {communityNumber !== undefined ? <Text>{bookingsText.resolve.community(communityNumber)}</Text> : null}
      <Text>{bookingsText.list.when(request)}</Text>
      <Text>{request.reason}</Text>
      {problem ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem}`}
        </Text>
      ) : null}
      {rejecting ? (
        <View className="gap-3">
          <ReasonField
            label={bookingsText.resolve.rejectionReason}
            value={reason}
            invalid={false}
            onChangeText={setReason}
          />
          <Button
            accessibilityLabel={bookingsText.resolve.confirmReject}
            disabled={working}
            onPress={() => void confirmRejection()}
          >
            <Text>{working ? bookingsText.resolve.working : bookingsText.resolve.confirmReject}</Text>
          </Button>
          <Button
            variant="outline"
            accessibilityLabel={bookingsText.resolve.cancelReject}
            disabled={working}
            onPress={() => setRejecting(false)}
          >
            <Text>{bookingsText.resolve.cancelReject}</Text>
          </Button>
        </View>
      ) : (
        <View className="gap-3">
          <Button
            accessibilityLabel={bookingsText.resolve.approveFor(roomName)}
            disabled={working}
            onPress={() => void run(() => onApprove(request.id))}
          >
            <Text>{working ? bookingsText.resolve.working : bookingsText.resolve.approve}</Text>
          </Button>
          <Button
            variant="outline"
            accessibilityLabel={bookingsText.resolve.rejectFor(roomName)}
            disabled={working}
            onPress={() => setRejecting(true)}
          >
            <Text>{bookingsText.resolve.reject}</Text>
          </Button>
        </View>
      )}
    </View>
  );
}
