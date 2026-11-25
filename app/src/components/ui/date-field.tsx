import DateTimePicker, { DateTimePickerAndroid, type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { useEffect, useRef, useState, type ComponentRef } from 'react';
import { AccessibilityInfo, Platform, Pressable, View, findNodeHandle, useColorScheme } from 'react-native';
import { Button } from '@/components/ui/button';
import type { DateFieldProps } from '@/components/ui/date-field-props';
import { Text } from '@/components/ui/text';
import { pickersText } from '@/i18n/es/pickers';
import { clampDate, dateFromValue, displayValue, spokenValue, valueFromDate } from '@/lib/bookings/date-value';
import { cn } from '@/lib/utils';

function DateField({
  label,
  mode,
  value,
  onChange,
  invalid = false,
  hint,
  minimumDate,
  maximumDate,
}: DateFieldProps) {
  const [open, setOpen] = useState(false);
  const pickerRef = useRef<ComponentRef<typeof View>>(null);
  const scheme = useColorScheme();
  const shown = displayValue(value, mode);
  const shownDate = dateFromValue(value, mode, clampDate(new Date(), minimumDate, maximumDate));

  useEffect(() => {
    if (!open) {
      return;
    }
    const handle = findNodeHandle(pickerRef.current);
    if (handle != null) {
      AccessibilityInfo.setAccessibilityFocus(handle);
    }
  }, [open]);

  function pick(event: DateTimePickerEvent, date?: Date) {
    if (event.type === 'set' && date) {
      onChange(valueFromDate(date, mode));
    }
  }

  function close() {
    if (value === '') {
      onChange(valueFromDate(shownDate, mode));
    }
    setOpen(false);
  }

  function press() {
    if (Platform.OS === 'android') {
      DateTimePickerAndroid.open({
        value: shownDate,
        mode,
        is24Hour: true,
        minimumDate,
        maximumDate,
        onChange: pick,
      });
      return;
    }
    if (open) {
      close();
      return;
    }
    setOpen(true);
  }

  return (
    <View className="gap-2">
      <Pressable
        role="button"
        accessibilityLabel={label}
        accessibilityHint={hint}
        accessibilityValue={{ text: spokenValue(value, mode) }}
        accessibilityState={{ expanded: open }}
        aria-invalid={invalid || undefined}
        onPress={press}
        className={cn(
          'min-h-13 flex-row items-center rounded-md border-field bg-background px-4 active:bg-accent',
          invalid ? 'border-destructive' : 'border-input',
          Platform.select({ web: 'cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-ring' }),
        )}
      >
        <Text className={shown === '' ? 'text-muted-foreground' : undefined}>{shown === '' ? pickersText.choose : shown}</Text>
      </Pressable>
      {open && Platform.OS !== 'android' ? (
        <View ref={pickerRef} className="gap-2">
          <DateTimePicker
            testID="date-field-picker"
            value={shownDate}
            mode={mode}
            display={mode === 'date' ? 'inline' : 'spinner'}
            locale="es-ES"
            is24Hour
            minimumDate={minimumDate}
            maximumDate={maximumDate}
            themeVariant={scheme === 'dark' ? 'dark' : 'light'}
            onChange={pick}
          />
          <Button variant="outline" accessibilityLabel={pickersText.close} onPress={close}>
            <Text>{pickersText.close}</Text>
          </Button>
        </View>
      ) : null}
    </View>
  );
}

export { DateField };
