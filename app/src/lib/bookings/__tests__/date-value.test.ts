import { addYears, clampDate, dateFromValue, displayValue, spokenValue, startOfDay, valueFromDate } from '../date-value';

describe('date value', () => {
  it('reads a date value as a local calendar day', () => {
    const date = dateFromValue('2030-05-11', 'date');

    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2030, 4, 11]);
  });

  it('reads a time value as a local time of day', () => {
    const date = dateFromValue('18:05', 'time');

    expect([date.getHours(), date.getMinutes()]).toEqual([18, 5]);
  });

  it('falls back to the given date when the value is empty or malformed', () => {
    const fallback = new Date(2031, 0, 2, 3, 4);

    expect(dateFromValue('', 'date', fallback)).toBe(fallback);
    expect(dateFromValue('11/05/2030', 'date', fallback)).toBe(fallback);
    expect(dateFromValue('6pm', 'time', fallback)).toBe(fallback);
  });

  it('writes a date as AAAA-MM-DD with zero padding', () => {
    expect(valueFromDate(new Date(2030, 0, 5, 23, 59), 'date')).toBe('2030-01-05');
  });

  it('writes a time as HH:MM with zero padding', () => {
    expect(valueFromDate(new Date(2030, 0, 5, 7, 4), 'time')).toBe('07:04');
  });

  it('round-trips both formats', () => {
    expect(valueFromDate(dateFromValue('2030-12-31', 'date'), 'date')).toBe('2030-12-31');
    expect(valueFromDate(dateFromValue('00:00', 'time'), 'time')).toBe('00:00');
  });

  it('shows a date as day, month and year and a time unchanged', () => {
    expect(displayValue('2030-05-11', 'date')).toBe('11/05/2030');
    expect(displayValue('18:00', 'time')).toBe('18:00');
  });

  it('shows nothing for an empty value', () => {
    expect(displayValue('', 'date')).toBe('');
  });

  it('speaks a date in a long format', () => {
    expect(spokenValue('2030-05-11', 'date')).toBe('11 de mayo de 2030');
  });

  it('speaks a time as hours and minutes', () => {
    expect(spokenValue('18:30', 'time')).toBe('18 horas y 30 minutos');
    expect(spokenValue('07:00', 'time')).toBe('7 horas');
    expect(spokenValue('', 'time')).toBe('');
  });

  it('starts a day at midnight', () => {
    const date = startOfDay(new Date(2030, 4, 11, 18, 45, 10));

    expect([date.getDate(), date.getHours(), date.getMinutes(), date.getSeconds()]).toEqual([11, 0, 0, 0]);
  });

  it('adds years keeping the day and falling back to the last day of the month', () => {
    expect(valueFromDate(addYears(new Date(2030, 4, 11), 1), 'date')).toBe('2031-05-11');
    expect(valueFromDate(addYears(new Date(2032, 1, 29), 1), 'date')).toBe('2033-02-28');
  });

  it('clamps a date between a minimum and a maximum', () => {
    const min = new Date(2030, 0, 10);
    const max = new Date(2030, 0, 20);

    expect(clampDate(new Date(2030, 0, 1), min, max)).toBe(min);
    expect(clampDate(new Date(2030, 0, 30), min, max)).toBe(max);
    const inside = new Date(2030, 0, 15);
    expect(clampDate(inside, min, max)).toBe(inside);
    expect(clampDate(inside)).toBe(inside);
  });
});
