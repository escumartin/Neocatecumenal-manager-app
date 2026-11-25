import { toneForBookingStatus } from '../status-tone';

describe('toneForBookingStatus', () => {
  it.each([
    ['PENDING', 'warning'],
    ['APPROVED', 'success'],
    ['REJECTED', 'destructive'],
    ['CANCELLED', 'neutral'],
  ] as const)('maps %s to %s', (status, tone) => {
    expect(toneForBookingStatus(status)).toBe(tone);
  });
});
