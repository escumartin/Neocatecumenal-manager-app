import { View } from 'react-native';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { bookingsText } from '@/i18n/es/bookings';
import { fieldHint } from './field-hint';
import { FieldProblem } from './field-problem';

export const REASON_MAX_LENGTH = 500;

type Props = {
  label: string;
  value: string;
  invalid: boolean;
  problem?: string | null;
  onChangeText: (value: string) => void;
};

export function ReasonField({ label, value, invalid, problem, onChangeText }: Props) {
  return (
    <View className="gap-2">
      <Text variant="strong">{label}</Text>
      <Text>{bookingsText.reasonWarning}</Text>
      <Input
        accessibilityLabel={label}
        accessibilityHint={fieldHint(bookingsText.reasonWarning, problem)}
        invalid={invalid}
        multiline
        numberOfLines={4}
        textAlignVertical="top"
        maxLength={REASON_MAX_LENGTH}
        value={value}
        onChangeText={onChangeText}
      />
      <FieldProblem message={problem} />
    </View>
  );
}
