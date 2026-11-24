import { messageForLoginFailure } from '../identity';
import { messageForUsersFailure, usersText } from '../users';

describe('messageForUsersFailure', () => {
  it('explains a password reset on the own account', () => {
    expect(messageForUsersFailure({ code: 'SELF_ACTION_FORBIDDEN' })).toBe(usersText.errors.SELF_ACTION_FORBIDDEN);
  });

  it.each(['BAD_REQUEST', 'METHOD_NOT_ALLOWED', 'NOT_ACCEPTABLE', 'UNSUPPORTED_MEDIA_TYPE', 'INTERNAL_ERROR'])(
    'shows the generic message for %s',
    (code) => {
      expect(messageForUsersFailure({ code })).toBe(usersText.errors.UNKNOWN);
    },
  );
});

describe('messageForLoginFailure', () => {
  it('explains a refused origin without the generic login text', () => {
    const forbidden = messageForLoginFailure('FORBIDDEN');
    expect(forbidden).not.toBe(messageForLoginFailure('UNKNOWN'));
  });

  it.each(['BAD_REQUEST', 'METHOD_NOT_ALLOWED', 'NOT_ACCEPTABLE', 'UNSUPPORTED_MEDIA_TYPE', 'INTERNAL_ERROR'])(
    'shows the generic login message for %s',
    (code) => {
      expect(messageForLoginFailure(code)).toBe(messageForLoginFailure('UNKNOWN'));
    },
  );
});
