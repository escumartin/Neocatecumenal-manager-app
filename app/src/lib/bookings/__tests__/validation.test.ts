import { firstInvalidSlotField, isDate, isTime } from '../validation';

describe('isDate', () => {
  it.each(['2030-05-11', ' 2030-05-11 '])('accepts %j', (value) => {
    expect(isDate(value)).toBe(true);
  });

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
