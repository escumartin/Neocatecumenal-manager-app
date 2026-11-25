import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { bookingsText, messageForBookingFailure } from '@/i18n/es/bookings';
import type { BookingRequestResult } from '@/lib/api/booking-requests-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { ReasonField } from './reason-field';

type Props = {
  initialReason: string;
  onSave: (reason: string) => Promise<BookingRequestResult>;
  onDone: () => void;
};

export function ReasonEditor({ initialReason, onSave, onDone }: Props) {
  const [reason, setReason] = useState(initialReason);
  const [problem, setProblem] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    const trimmed = reason.trim();
    if (trimmed.length === 0) {
      setProblem(bookingsText.fieldErrors.REQUIRED);
      return;
    }
    setSaving(true);
    setProblem(null);
    try {
      const result = await onSave(trimmed);
      if (result.ok) {
        hapticSuccess();
        onDone();
      } else {
        hapticFailure();
        setProblem(messageForBookingFailure(result));
      }
    } catch {
      hapticFailure();
      setProblem(bookingsText.errors.UNKNOWN);
    } finally {
      setSaving(false);
    }
  }

  return (
    <View className="gap-3">
      <ReasonField
        label={bookingsText.rectify.reason}
        value={reason}
        invalid={problem !== null}
        onChangeText={setReason}
      />
      {problem ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem}`}
        </Text>
      ) : null}
      <Button accessibilityLabel={bookingsText.rectify.save} disabled={saving} onPress={() => void save()}>
        <Text>{saving ? bookingsText.rectify.saving : bookingsText.rectify.save}</Text>
      </Button>
      <Button variant="outline" accessibilityLabel={bookingsText.rectify.cancel} disabled={saving} onPress={onDone}>
        <Text>{bookingsText.rectify.cancel}</Text>
      </Button>
    </View>
  );
}
