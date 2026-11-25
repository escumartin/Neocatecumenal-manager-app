const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

export type SlotField = 'date' | 'startTime' | 'endTime';

export function isDate(value: string): boolean {
  return DATE_PATTERN.test(value.trim());
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
