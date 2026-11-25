import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { messageForRoomsFailure, roomsText } from '@/i18n/es/rooms';
import type { RoomResult } from '@/lib/api/rooms-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

type Props = {
  label: string;
  submitLabel: string;
  submittingLabel: string;
  initialName?: string;
  clearOnSuccess?: boolean;
  onSubmit: (name: string) => Promise<RoomResult>;
  onCancel?: () => void;
  cancelLabel?: string;
};

export function RoomNameForm({
  label,
  submitLabel,
  submittingLabel,
  initialName = '',
  clearOnSuccess = false,
  onSubmit,
  onCancel,
  cancelLabel,
}: Props) {
  const [name, setName] = useState(initialName);
  const [problem, setProblem] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    const trimmed = name.trim();
    if (trimmed.length === 0) {
      setProblem(roomsText.fieldErrors.REQUIRED);
      return;
    }
    setSubmitting(true);
    setProblem(null);
    try {
      const result = await onSubmit(trimmed);
      if (!result.ok) {
        hapticFailure();
        setProblem(messageForRoomsFailure(result));
      } else {
        hapticSuccess();
        if (clearOnSuccess) {
          setName('');
        }
      }
    } catch {
      hapticFailure();
      setProblem(roomsText.errors.UNKNOWN);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View className="gap-3">
      <Text variant="strong">{label}</Text>
      <Input
        accessibilityLabel={label}
        accessibilityHint={problem ?? undefined}
        invalid={problem !== null}
        maxLength={80}
        value={name}
        onChangeText={setName}
      />
      {problem ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem}`}
        </Text>
      ) : null}
      <Button accessibilityLabel={submitLabel} disabled={submitting} onPress={() => void submit()}>
        <Text>{submitting ? submittingLabel : submitLabel}</Text>
      </Button>
      {onCancel && cancelLabel ? (
        <Button variant="outline" accessibilityLabel={cancelLabel} disabled={submitting} onPress={onCancel}>
          <Text>{cancelLabel}</Text>
        </Button>
      ) : null}
    </View>
  );
}
