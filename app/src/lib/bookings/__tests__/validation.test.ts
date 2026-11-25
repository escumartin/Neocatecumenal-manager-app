import { firstInvalidSlotField, isDate, isTime, recurrenceUntilProblem } from '../validation';

describe('isDate', () => {
  it.each(['2030-05-11', ' 2030-05-11 '])('accepts %j', (value) => {
    expect(isDate(value)).toBe(true);
  });

  it.each(['2028-02-29', '2030-12-31'])('accepts the existing day %j', (value) => {
    expect(isDate(value)).toBe(true);
  });

  it.each(['2030-02-31', '2029-02-29', '2030-04-31', '2030-13-01', '2030-00-10', '2030-05-00'])(
    'rejects the impossible day %j',
    (value) => {
      expect(isDate(value)).toBe(false);
    },
  );

  it.each(['', '11/05/2030', '2030-5-11', '2030-05-11T10:00', 'hoy'])('rejects %j', (value) => {
    expect(isDate(value)).toBe(false);
  });
});

describe('isTime', () => {
  it.each(['00:00', '09:30', '23:59', ' 18:00 '])('accepts %j', (value) => {
    expect(isTime(value)).toBe(true);
  });

  it.each(['', '24:00', '18:60', '9:30', '18-00', '18:00:00'])('rejects %j', (value) => {
    expect(isTime(value)).toBe(false);
  });
});

describe('firstInvalidSlotField', () => {
  const valid = { date: '2030-05-11', startTime: '18:00', endTime: '20:00' };

  it('is null when date and times are valid', () => {
    expect(firstInvalidSlotField(valid)).toBeNull();
  });

  it.each([
    [{ date: 'x' }, 'date'],
    [{ startTime: 'x' }, 'startTime'],
    [{ endTime: 'x' }, 'endTime'],
    [{ date: 'x', startTime: 'x', endTime: 'x' }, 'date'],
    [{ startTime: 'x', endTime: 'x' }, 'startTime'],
  ])('reports %j as %s', (override, field) => {
    expect(firstInvalidSlotField({ ...valid, ...override })).toBe(field);
  });
});

describe('recurrenceUntilProblem', () => {
  it.each([
    ['2030-05-11', '2030-05-11'],
    ['2030-05-11', '2030-07-02'],
    ['2030-05-11', '2031-05-11'],
    ['2028-02-29', '2029-02-28'],
    ['2028-02-29', '2028-02-29'],
    ['2030-12-31', '2031-12-31'],
  ])('accepts %s until %s', (date, until) => {
    expect(recurrenceUntilProblem(date, until)).toBeNull();
  });

  it.each([
    ['2030-05-11', '2030-05-10'],
    ['2030-01-01', '2029-12-31'],
    ['2028-02-29', '2028-02-28'],
  ])('reports RECURRENCE_UNTIL_BEFORE_DATE for %s until %s', (date, until) => {
    expect(recurrenceUntilProblem(date, until)).toBe('RECURRENCE_UNTIL_BEFORE_DATE');
  });

  it.each([
    ['2030-05-11', '2031-05-12'],
    ['2028-02-29', '2029-03-01'],
    ['2030-12-31', '2032-01-01'],
  ])('reports RECURRENCE_TOO_LONG for %s until %s', (date, until) => {
    expect(recurrenceUntilProblem(date, until)).toBe('RECURRENCE_TOO_LONG');
  });

  it.each([
    ['', '2030-07-02'],
    ['2030-05-11', ''],
    ['hoy', '2030-07-02'],
    ['2030-05-11', '02/07/2030'],
  ])('reports nothing when %j or %j is not a date', (date, until) => {
    expect(recurrenceUntilProblem(date, until)).toBeNull();
  });

  it('ignores surrounding spaces', () => {
    expect(recurrenceUntilProblem(' 2030-05-11 ', ' 2031-05-11 ')).toBeNull();
  });
});
