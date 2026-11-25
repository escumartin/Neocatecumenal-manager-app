import type { components } from '@/api/schema';
import { validateContactValue, type ContactFieldName } from './contact-validation';

export const CONTACT_FIELDS: ContactFieldName[] = ['mobile', 'landline', 'email'];

export type ContactDraftField = { shared: boolean; value: string };
export type ContactDraft = Record<ContactFieldName, ContactDraftField>;
export type ContactDraftErrors = Partial<Record<ContactFieldName, 'REQUIRED' | 'PHONE_INVALID' | 'EMAIL_INVALID'>>;
type ContactInput = components['schemas']['ContactInput'];
type ContactValues = Partial<Record<ContactFieldName, string | undefined>>;

export function draftFrom(member: ContactValues | undefined): ContactDraft {
  const field = (name: ContactFieldName): ContactDraftField => {
    const value = member?.[name];
    return value ? { shared: true, value } : { shared: false, value: '' };
  };
  return { mobile: field('mobile'), landline: field('landline'), email: field('email') };
}

export function hasSharedChanges(initial: ContactDraft, draft: ContactDraft): boolean {
  return CONTACT_FIELDS.some((name) => initial[name].shared !== draft[name].shared);
}

export function validateDraft(draft: ContactDraft): ContactDraftErrors {
  const errors: ContactDraftErrors = {};
  for (const name of CONTACT_FIELDS) {
    if (draft[name].shared) {
      const result = validateContactValue(name, draft[name].value);
      if (!result.ok) {
        errors[name] = result.code;
      }
    }
  }
  return errors;
}

export function toContactInput(draft: ContactDraft): ContactInput {
  const field = (name: ContactFieldName) => {
    const { shared, value } = draft[name];
    if (!shared) {
      return { shared: false };
    }
    const result = validateContactValue(name, value);
    return { shared: true, value: result.ok ? result.value : value.trim() };
  };
  return { mobile: field('mobile'), landline: field('landline'), email: field('email') };
}
