import { isBelowMinimum } from '../app-version';

describe('app version comparison', () => {
  it('compares components numerically, not as text', () => {
    expect(isBelowMinimum('1.2.0', '1.10.0')).toBe(true);
  });

  it('treats a lower major version as below the minimum', () => {
    expect(isBelowMinimum('1.9.9', '2.0.0')).toBe(true);
  });

  it('treats a lower patch version as below the minimum', () => {
    expect(isBelowMinimum('1.0.0', '1.0.1')).toBe(true);
  });

  it('does not block an equal version', () => {
    expect(isBelowMinimum('1.0.0', '1.0.0')).toBe(false);
  });

  it('does not block a greater version', () => {
    expect(isBelowMinimum('1.10.0', '1.2.0')).toBe(false);
    expect(isBelowMinimum('2.0.0', '1.99.99')).toBe(false);
  });

  it('does not block when the current version is malformed', () => {
    expect(isBelowMinimum('abc', '1.0.0')).toBe(false);
    expect(isBelowMinimum('1.0', '1.0.0')).toBe(false);
    expect(isBelowMinimum('', '1.0.0')).toBe(false);
  });

  it('does not block when the minimum version is malformed', () => {
    expect(isBelowMinimum('1.0.0', 'x.y.z')).toBe(false);
    expect(isBelowMinimum('1.0.0', '1.0.0.1')).toBe(false);
  });
});
