export type ContactFieldName = 'mobile' | 'landline' | 'email';

export type ContactValidation =
  | { ok: true; value: string }
  | { ok: false; code: 'REQUIRED' | 'PHONE_INVALID' | 'EMAIL_INVALID' };

export const PHONE_PATTERN = /^([6-9][0-9]{8}|\+[1-9][0-9]{1,14})$/;

export const EMAIL_PATTERN =
  /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;

const EMAIL_MAX_LENGTH = 254;
const PHONE_SEPARATORS = /[ \t\n\u000B\f\r.\-()]/g;

export function validateContactValue(field: ContactFieldName, typed: string): ContactValidation {
  if (typed.trim().length === 0) {
    return { ok: false, code: 'REQUIRED' };
  }
  if (field === 'email') {
    const email = typed.trim();
    return email.length <= EMAIL_MAX_LENGTH && EMAIL_PATTERN.test(email)
      ? { ok: true, value: email }
      : { ok: false, code: 'EMAIL_INVALID' };
  }
  const phone = typed.replace(PHONE_SEPARATORS, '');
  return PHONE_PATTERN.test(phone) ? { ok: true, value: phone } : { ok: false, code: 'PHONE_INVALID' };
}
