import type { PickerMode } from '@/lib/bookings/date-value';

export type DateFieldProps = {
  label: string;
  mode: PickerMode;
  value: string;
  onChange: (value: string) => void;
  invalid?: boolean;
  hint?: string;
  minimumDate?: Date;
  maximumDate?: Date;
};
