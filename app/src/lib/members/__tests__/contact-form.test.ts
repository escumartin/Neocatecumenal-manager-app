import { draftFrom, hasSharedChanges, toContactInput, validateDraft, type ContactDraft } from '../contact-form';

const empty = draftFrom(undefined);

function draft(overrides: Partial<ContactDraft>): ContactDraft {
  return { ...empty, ...overrides };
}

describe('contact draft', () => {
  it('starts with every field unshared and blank for a new member', () => {
    expect(empty).toEqual({
      mobile: { shared: false, value: '' },
      landline: { shared: false, value: '' },
      email: { shared: false, value: '' },
    });
  });

  it('marks as shared the fields the member already shares', () => {
    expect(draftFrom({ mobile: '600000001', email: 'ana@example.org' })).toEqual({
      mobile: { shared: true, value: '600000001' },
      landline: { shared: false, value: '' },
      email: { shared: true, value: 'ana@example.org' },
    });
  });

  it('detects a change in what is shared but not a rectification of a value', () => {
    const initial = draftFrom({ mobile: '600000001' });

    expect(hasSharedChanges(initial, draft({ mobile: { shared: true, value: '600000002' } }))).toBe(false);
    expect(hasSharedChanges(initial, { ...initial, email: { shared: true, value: 'ana@example.org' } })).toBe(true);
    expect(hasSharedChanges(initial, { ...initial, mobile: { shared: false, value: '' } })).toBe(true);
  });

  it('sends every field explicitly, with the value only when shared', () => {
    const input = toContactInput(
      draft({
        mobile: { shared: true, value: ' 600 000 001 ' },
        landline: { shared: false, value: '912345678' },
        email: { shared: true, value: ' ana@example.org ' },
      }),
    );

    expect(input).toEqual({
      mobile: { shared: true, value: '600000001' },
      landline: { shared: false },
      email: { shared: true, value: 'ana@example.org' },
    });
  });
});

describe('contact draft validation', () => {
  it('accepts a draft with nothing shared', () => {
    expect(validateDraft(empty)).toEqual({});
  });

  it('ignores what was typed in a field that is not shared', () => {
    expect(validateDraft(draft({ mobile: { shared: false, value: 'abc' } }))).toEqual({});
  });

  it('reports each shared field that is blank or malformed with its code', () => {
    const errors = validateDraft({
      mobile: { shared: true, value: '' },
      landline: { shared: true, value: '123' },
      email: { shared: true, value: 'a@b' },
    });

    expect(errors).toEqual({ mobile: 'REQUIRED', landline: 'PHONE_INVALID', email: 'EMAIL_INVALID' });
  });
});
