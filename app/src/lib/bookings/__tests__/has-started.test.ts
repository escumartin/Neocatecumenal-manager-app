import { hasStarted } from '../has-started';

describe('hasStarted', () => {
  it('is false before the start time in Madrid', () => {
    expect(
      hasStarted('2030-05-10', '18:00', new Date('2030-05-10T15:59:00Z')),
    ).toBe(false);
  });

  it('is true at the exact start time in Madrid', () => {
    expect(
      hasStarted('2030-05-10', '18:00', new Date('2030-05-10T16:00:00Z')),
    ).toBe(true);
  });

  it('is true after the start time', () => {
    expect(
      hasStarted('2030-05-10', '18:00', new Date('2030-05-10T19:00:00Z')),
    ).toBe(true);
  });

  it('uses winter time in winter', () => {
    expect(
      hasStarted('2030-01-10', '18:00', new Date('2030-01-10T16:59:00Z')),
    ).toBe(false);
    expect(
      hasStarted('2030-01-10', '18:00', new Date('2030-01-10T17:00:00Z')),
    ).toBe(true);
  });

  it('is true for a past date and false for a future one', () => {
    const now = new Date('2030-05-10T10:00:00Z');

    expect(hasStarted('2030-05-09', '23:00', now)).toBe(true);
    expect(hasStarted('2030-05-11', '00:30', now)).toBe(false);
  });

  it('reads the Madrid date when it differs from the UTC date', () => {
    expect(
      hasStarted('2030-05-11', '00:30', new Date('2030-05-10T22:45:00Z')),
    ).toBe(true);
    expect(
      hasStarted('2030-05-11', '00:30', new Date('2030-05-10T22:15:00Z')),
    ).toBe(false);
  });
});
