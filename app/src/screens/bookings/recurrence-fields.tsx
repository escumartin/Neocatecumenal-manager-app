import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { bookingsText } from '@/i18n/es/bookings';
import { FieldProblem } from './field-problem';

type Props = {
  enabled: boolean;
  until: string;
  invalid: boolean;
  problem?: string | null;
  onToggle: (enabled: boolean) => void;
  onChangeUntil: (until: string) => void;
};

export function RecurrenceFields({ enabled, until, invalid, problem, onToggle, onChangeUntil }: Props) {
  return (
    <View className="gap-3">
      <Button
        role="checkbox"
        variant={enabled ? 'default' : 'outline'}
        accessibilityLabel={bookingsText.recurrence.toggle}
        aria-checked={enabled}
        accessibilityState={{ checked: enabled }}
        onPress={() => onToggle(!enabled)}
      >
        <Text>{enabled ? `✓ ${bookingsText.recurrence.toggle}` : bookingsText.recurrence.toggle}</Text>
      </Button>
      {enabled ? (
        <View className="gap-2">
          <Text variant="strong">{bookingsText.recurrence.until}</Text>
          <Text>{bookingsText.recurrence.hint}</Text>
          <Input
            accessibilityLabel={bookingsText.recurrence.until}
            accessibilityHint={problem ?? undefined}
            invalid={invalid}
            keyboardType="numbers-and-punctuation"
            maxLength={10}
            value={until}
            onChangeText={onChangeUntil}
          />
          <FieldProblem message={problem} />
        </View>
      ) : null}
    </View>
  );
}
