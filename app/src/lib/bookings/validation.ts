import { addYears } from '@/lib/bookings/calendar';

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

export type SlotField = 'date' | 'startTime' | 'endTime';

export function isDate(value: string): boolean {
  const text = value.trim();
  if (!DATE_PATTERN.test(text)) {
    return false;
  }
  const [year, month, day] = text.split('-').map(Number);
  const parsed = new Date(year, month - 1, day);
  return parsed.getFullYear() === year && parsed.getMonth() === month - 1 && parsed.getDate() === day;
}

export function isTime(value: string): boolean {
  return TIME_PATTERN.test(value.trim());
}

export function firstInvalidSlotField(slot: Record<SlotField, string>): SlotField | null {
  if (!isDate(slot.date)) {
    return 'date';
  }
  if (!isTime(slot.startTime)) {
    return 'startTime';
  }
  if (!isTime(slot.endTime)) {
    return 'endTime';
  }
  return null;
}

export type RecurrenceUntilProblem = 'RECURRENCE_UNTIL_BEFORE_DATE' | 'RECURRENCE_TOO_LONG';

function oneYearAfter(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  const next = addYears(new Date(year, month - 1, day), 1);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())}`;
}

export function recurrenceUntilProblem(date: string, until: string): RecurrenceUntilProblem | null {
  const first = date.trim();
  const last = until.trim();
  if (!isDate(first) || !isDate(last)) {
    return null;
  }
  if (last < first) {
    return 'RECURRENCE_UNTIL_BEFORE_DATE';
  }
  if (last > oneYearAfter(first)) {
    return 'RECURRENCE_TOO_LONG';
  }
  return null;
}
