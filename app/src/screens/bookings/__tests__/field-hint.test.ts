import { fieldHint } from '../field-hint';

describe('fieldHint', () => {
  it('joins the present parts with a space', () => {
    expect(fieldHint('Warning.', 'Problem.')).toBe('Warning. Problem.');
  });

  it('skips missing and empty parts', () => {
    expect(fieldHint('Warning.', null, undefined, '')).toBe('Warning.');
  });

  it('is undefined when there is nothing to say', () => {
    expect(fieldHint(null, undefined)).toBeUndefined();
  });
});
