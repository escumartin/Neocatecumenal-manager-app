import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { ListSection } from '@/components/ui/list-section';
import { SwitchRow } from '@/components/ui/switch-row';
import { Text } from '@/components/ui/text';
import { membersText } from '@/i18n/es/members';
import {
  CONTACT_FIELDS,
  type ContactDraft,
  type ContactDraftErrors,
  type ContactDraftField,
} from '@/lib/members/contact-form';
import type { ContactFieldName } from '@/lib/members/contact-validation';

type Props = {
  draft: ContactDraft;
  messages: Partial<Record<ContactFieldName, string>>;
  shareLocked?: boolean;
  onChange: (field: ContactFieldName, next: ContactDraftField) => void;
};

const KEYBOARDS = { mobile: 'phone-pad', landline: 'phone-pad', email: 'email-address' } as const;
const MAX_LENGTH = { mobile: 24, landline: 24, email: 254 } as const;

export function ContactFields({ draft, messages, shareLocked = false, onChange }: Props) {
  return (
    <>
      {CONTACT_FIELDS.map((name) => {
        const field = draft[name];
        const text = membersText.contact.fields[name];
        const problem = messages[name];
        return (
          <ListSection key={name} title={text.label}>
            <SwitchRow
              label={membersText.contact.share[name]}
              value={field.shared}
              disabled={shareLocked}
              onValueChange={(shared) => onChange(name, { shared, value: shared ? field.value : '' })}
            />
            {field.shared ? (
              <FormField description={text.hint}>
                <Input
                  accessibilityLabel={text.label}
                  accessibilityHint={problem}
                  invalid={problem !== undefined}
                  keyboardType={KEYBOARDS[name]}
                  autoCapitalize="none"
                  autoCorrect={false}
                  maxLength={MAX_LENGTH[name]}
                  value={field.value}
                  onChangeText={(value) => onChange(name, { shared: true, value })}
                />
                {problem ? (
                  <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
                    {`⚠ ${problem}`}
                  </Text>
                ) : null}
              </FormField>
            ) : null}
          </ListSection>
        );
      })}
    </>
  );
}

export function contactMessages(errors: ContactDraftErrors): Partial<Record<ContactFieldName, string>> {
  const messages: Partial<Record<ContactFieldName, string>> = {};
  for (const name of CONTACT_FIELDS) {
    const code = errors[name];
    if (code) {
      messages[name] = membersText.fieldErrors[name][code];
    }
  }
  return messages;
}
