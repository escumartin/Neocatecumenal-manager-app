import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { DateField } from '@/components/ui/date-field';
import { FormField } from '@/components/ui/form-field';
import { ListSection } from '@/components/ui/list-section';
import { Text } from '@/components/ui/text';
import { bookingsText } from '@/i18n/es/bookings';
import { addYears, dateFromValue, startOfDay } from '@/lib/bookings/date-value';
import { FieldProblem } from './field-problem';

type Props = {
  enabled: boolean;
  until: string;
  firstDate?: string;
  invalid: boolean;
  problem?: string | null;
  onToggle: (enabled: boolean) => void;
  onChangeUntil: (until: string) => void;
};

export function RecurrenceFields({ enabled, until, firstDate = '', invalid, problem, onToggle, onChangeUntil }: Props) {
  const minimumDate = dateFromValue(firstDate, 'date', startOfDay(new Date()));
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
        <ListSection title={bookingsText.recurrence.until}>
          <FormField description={bookingsText.recurrence.hint}>
            <DateField
              label={bookingsText.recurrence.until}
              mode="date"
              value={until}
              hint={problem ?? undefined}
              invalid={invalid}
              minimumDate={minimumDate}
              maximumDate={addYears(minimumDate, 1)}
              onChange={onChangeUntil}
            />
            <FieldProblem message={problem} />
          </FormField>
        </ListSection>
      ) : null}
    </View>
  );
}
