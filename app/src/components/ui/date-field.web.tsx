import type { DateFieldProps } from '@/components/ui/date-field-props';
import { valueFromDate } from '@/lib/bookings/date-value';
import { cn } from '@/lib/utils';

function DateField({ label, mode, value, onChange, invalid = false, hint, minimumDate, maximumDate }: DateFieldProps) {
  return (
    <input
      type={mode}
      aria-label={label}
      aria-description={hint}
      aria-invalid={invalid || undefined}
      min={minimumDate ? valueFromDate(minimumDate, mode) : undefined}
      max={maximumDate ? valueFromDate(maximumDate, mode) : undefined}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      className={cn(
        'min-h-13 rounded-md border-field bg-background px-4 font-sans text-body text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring',
        invalid ? 'border-destructive' : 'border-input',
      )}
    />
  );
}

export { DateField };
