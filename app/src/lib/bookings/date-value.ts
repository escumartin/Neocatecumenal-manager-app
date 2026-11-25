import { pickersText } from '@/i18n/es/pickers';
import { addYears } from '@/lib/bookings/calendar';
import { isDate, isTime } from '@/lib/bookings/validation';

export { addYears };

export type PickerMode = 'date' | 'time';

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function dateFromValue(value: string, mode: PickerMode, fallback: Date = new Date()): Date {
  const text = value.trim();
  if (mode === 'date' && isDate(text)) {
    const [year, month, day] = text.split('-').map(Number);
    return new Date(year, month - 1, day);
  }
  if (mode === 'time' && isTime(text)) {
    const [hours, minutes] = text.split(':').map(Number);
    const date = new Date(fallback);
    date.setHours(hours, minutes, 0, 0);
    return date;
  }
  return fallback;
}

export function valueFromDate(date: Date, mode: PickerMode): string {
  if (mode === 'date') {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function displayValue(value: string, mode: PickerMode): string {
  if (value === '') {
    return '';
  }
  if (mode === 'date' && isDate(value)) {
    const [year, month, day] = value.trim().split('-');
    return `${day}/${month}/${year}`;
  }
  return value;
}

const longDate = new Intl.DateTimeFormat('es-ES', { dateStyle: 'long' });

export function spokenValue(value: string, mode: PickerMode): string {
  if (value === '') {
    return '';
  }
  if (mode === 'date' && isDate(value)) {
    return longDate.format(dateFromValue(value, mode));
  }
  if (mode === 'time' && isTime(value)) {
    const [hours, minutes] = value.trim().split(':').map(Number);
    return pickersText.spokenTime(hours, minutes);
  }
  return value;
}

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function clampDate(date: Date, minimum?: Date, maximum?: Date): Date {
  if (minimum && date < minimum) {
    return minimum;
  }
  if (maximum && date > maximum) {
    return maximum;
  }
  return date;
}
