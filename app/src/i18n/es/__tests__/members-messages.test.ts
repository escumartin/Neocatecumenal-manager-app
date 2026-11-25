import { membersText, problemForMembersFailure } from '../members';

describe('problemForMembersFailure', () => {
  it.each([
    ['mobile', 'TOO_LONG'],
    ['landline', 'TOO_LONG'],
    ['email', 'TOO_LONG'],
    ['givenName', 'INVALID_FORMAT'],
    ['surnames', 'INVALID_FORMAT'],
  ])('explains %s %s next to its field', (field, code) => {
    const problem = problemForMembersFailure({
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field, code }],
    });

    expect(problem.field).toBe(field);
    expect(problem.message).toBe(membersText.fieldErrors[field][code]);
    expect(problem.message).not.toBe(membersText.errors.VALIDATION_FAILED);
  });

  it('tells that names cannot contain control characters or line breaks', () => {
    expect(membersText.fieldErrors.givenName.INVALID_FORMAT).toBe(
      'No puede contener caracteres de control ni saltos de línea.',
    );
  });
});
