import { parseCommunityNumber } from '../parse-community-number';

describe('parseCommunityNumber', () => {
  it.each([
    ['1', 1],
    ['12', 12],
    ['  40 ', 40],
    ['007', 7],
    ['2147483647', 2147483647],
  ])('accepts %j as %d', (typed, value) => {
    expect(parseCommunityNumber(typed)).toEqual({ ok: true, value });
  });

  it.each(['', '   '])('reports %j as required', (typed) => {
    expect(parseCommunityNumber(typed)).toEqual({ ok: false, code: 'REQUIRED' });
  });

  it.each(['0', '-3', '1.5', '1,5', 'abc', '12a', '2147483648', '99999999999'])('reports %j as invalid', (typed) => {
    expect(parseCommunityNumber(typed)).toEqual({ ok: false, code: 'INVALID_FORMAT' });
  });
});
