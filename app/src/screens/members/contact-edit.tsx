import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormScroll } from '@/components/ui/form-scroll';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useMember } from '@/hooks/members/use-member';
import { useMemberContactSave } from '@/hooks/members/use-member-contact-save';
import { membersText, problemForMembersFailure, type MemberProblem } from '@/i18n/es/members';
import type { Member, MemberContactInput, MemberManagementApi } from '@/lib/api/members-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import {
  draftFrom,
  hasSharedChanges,
  toContactInput,
  validateDraft,
  type ContactDraft,
  type ContactDraftErrors,
} from '@/lib/members/contact-form';
import { ConsentSection, type ConsentChannelChoice } from './consent-section';
import { ContactFields, contactMessages } from './contact-fields';
import { fullNameOf } from './member-name';

type Props = {
  memberId: string;
  api?: MemberManagementApi;
  onSaved: () => void;
};

export function MemberContactEdit({ memberId, api, onSaved }: Props) {
  const { status, member, reload } = useMember(memberId, api);

  if (status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="loading" message={membersText.form.loading} />
      </View>
    );
  }

  if (status === 'error' || member === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView
          kind="error"
          message={membersText.form.loadError}
          retryLabel={membersText.retry}
          onRetry={() => void reload()}
        />
      </View>
    );
  }

  return <ContactForm member={member} api={api} onSaved={onSaved} />;
}

type FormProps = { member: Member; api?: MemberManagementApi; onSaved: () => void };

function ContactForm({ member, api, onSaved }: FormProps) {
  const { save } = useMemberContactSave(member.id, api);
  const linked = member.linkedUserId !== undefined;
  const [initial] = useState<ContactDraft>(() => draftFrom(member));
  const [draft, setDraft] = useState<ContactDraft>(initial);
  const [contactErrors, setContactErrors] = useState<ContactDraftErrors>({});
  const [channel, setChannel] = useState<ConsentChannelChoice | null>(null);
  const [noticeGiven, setNoticeGiven] = useState(false);
  const [problem, setProblem] = useState<MemberProblem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const needsConsent = !linked && hasSharedChanges(initial, draft);

  function fail(next: MemberProblem | null, errors: ContactDraftErrors = {}) {
    setProblem(next);
    setContactErrors(errors);
    hapticFailure();
  }

  async function submit() {
    const errors = validateDraft(draft);
    if (Object.keys(errors).length > 0) {
      fail(null, errors);
      return;
    }
    if (needsConsent && channel === null) {
      fail({ field: null, message: membersText.errors.CONSENT_REQUIRED });
      return;
    }
    if (needsConsent && !noticeGiven) {
      fail({ field: null, message: membersText.errors.NOTICE_NOT_GIVEN });
      return;
    }
    const input: MemberContactInput = {
      ...toContactInput(draft),
      ...(needsConsent && channel ? { onBehalfConsent: { channel, noticeGiven } } : {}),
    };
    setSubmitting(true);
    setProblem(null);
    setContactErrors({});
    try {
      const result = await save(input);
      if (result.ok) {
        hapticSuccess();
        onSaved();
        return;
      }
      fail(problemForMembersFailure(result));
    } catch {
      fail({ field: null, message: membersText.errors.UNKNOWN });
    } finally {
      setSubmitting(false);
    }
  }

  const messages = contactMessages(contactErrors);
  if (problem?.field === 'mobile' || problem?.field === 'landline' || problem?.field === 'email') {
    messages[problem.field] = problem.message;
  }

  return (
    <FormScroll>
      <Text variant="title">{membersText.contact.title}</Text>
      <Text variant="strong">{fullNameOf(member)}</Text>
      {linked ? <Text>{membersText.contact.linkedNotice}</Text> : null}
      <ContactFields
        draft={draft}
        messages={messages}
        shareLocked={linked}
        onChange={(field, next) => setDraft((current) => ({ ...current, [field]: next }))}
      />
      {needsConsent ? (
        <ConsentSection
          channel={channel}
          noticeGiven={noticeGiven}
          onChannel={setChannel}
          onNoticeGiven={setNoticeGiven}
        />
      ) : null}
      {problem && problem.field === null ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem.message}`}
        </Text>
      ) : null}
      <Button accessibilityLabel={membersText.contact.save} disabled={submitting} onPress={submit}>
        <Text>{submitting ? membersText.form.saving : membersText.contact.save}</Text>
      </Button>
    </FormScroll>
  );
}
