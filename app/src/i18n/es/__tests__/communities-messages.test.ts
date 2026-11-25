import { communitiesText, messageForCommunitiesFailure } from '../communities';

describe('messageForCommunitiesFailure', () => {
  it('explains control characters in the name apart from a wrong number', () => {
    const name = messageForCommunitiesFailure({
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'name', code: 'INVALID_FORMAT' }],
    });
    const number = messageForCommunitiesFailure({
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'number', code: 'INVALID_FORMAT' }],
    });

    expect(name).toBe('No puede contener caracteres de control ni saltos de línea.');
    expect(number).toBe(communitiesText.fieldErrors.INVALID_FORMAT);
  });
});
