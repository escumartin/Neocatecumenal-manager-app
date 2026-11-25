import { validateContactValue } from '../contact-validation';

describe('phone validation', () => {
  it.each([
    ['600 000 001', '600000001'],
    ['600-000-003', '600000003'],
    ['+351600000003', '+351600000003'],
    ['+34 (600) 000.003', '+34600000003'],
    [' 700000002 ', '700000002'],
    ['800000003', '800000003'],
    ['900000004', '900000004'],
    ['+12', '+12'],
  ])('accepts %s as %s for the mobile and the landline', (typed, normalized) => {
    expect(validateContactValue('mobile', typed)).toEqual({ ok: true, value: normalized });
    expect(validateContactValue('landline', typed)).toEqual({ ok: true, value: normalized });
  });

  it.each(['500000005', '60000000', '6000000011', '+1234567890123456', '+0123456789', 'abc', '600 000 00a', '+'])(
    'rejects %s with PHONE_INVALID',
    (typed) => {
      expect(validateContactValue('mobile', typed)).toEqual({ ok: false, code: 'PHONE_INVALID' });
      expect(validateContactValue('landline', typed)).toEqual({ ok: false, code: 'PHONE_INVALID' });
    },
  );

  it.each(['', '   '])('asks for a value when the phone is blank (%j)', (typed) => {
    expect(validateContactValue('mobile', typed)).toEqual({ ok: false, code: 'REQUIRED' });
  });
});

describe('email validation', () => {
  it.each(['ana@example.org', 'ana.perez+parish@mail.example.org', 'a_b-c@x-y.es', "o'neil@example.com"])(
    'accepts %s',
    (typed) => {
      expect(validateContactValue('email', typed)).toEqual({ ok: true, value: typed });
    },
  );

  it('trims the address', () => {
    expect(validateContactValue('email', '  ana@example.org  ')).toEqual({ ok: true, value: 'ana@example.org' });
  });

  it.each([
    'a@b',
    'ana',
    '@example.org',
    'ana@',
    'ana@@example.org',
    'ana@-example.org',
    'ana@example..org',
    'ana perez@example.org',
    'ana@example.org.',
  ])('rejects %s with EMAIL_INVALID', (typed) => {
    expect(validateContactValue('email', typed)).toEqual({ ok: false, code: 'EMAIL_INVALID' });
  });

  it('asks for a value when the email is blank', () => {
    expect(validateContactValue('email', '   ')).toEqual({ ok: false, code: 'REQUIRED' });
  });

  it('accepts an address of exactly 254 characters and rejects one of 255', () => {
    const at254 = `${'a'.repeat(64)}@${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(58)}.es`;
    const at255 = `${'a'.repeat(64)}@${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(59)}.es`;

    expect(at254).toHaveLength(254);
    expect(validateContactValue('email', at254)).toEqual({ ok: true, value: at254 });
    expect(validateContactValue('email', at255)).toEqual({ ok: false, code: 'EMAIL_INVALID' });
  });
});
