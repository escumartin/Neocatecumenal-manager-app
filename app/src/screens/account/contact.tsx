import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormScroll } from '@/components/ui/form-scroll';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useOwnMember } from '@/hooks/members/use-own-member';
import { useSession } from '@/hooks/session/use-session';
import { communitiesText } from '@/i18n/es/communities';
import { membersText, problemForMembersFailure, type MemberProblem } from '@/i18n/es/members';
import type { MyMember, MyMemberApi } from '@/lib/api/members-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import {
  draftFrom,
  toContactInput,
  validateDraft,
  type ContactDraft,
  type ContactDraftErrors,
} from '@/lib/members/contact-form';
import type { ContactFieldName } from '@/lib/members/contact-validation';
import { ContactFields, contactMessages } from '@/screens/members/contact-fields';
import { fullNameOf } from '@/screens/members/member-name';

type Props = {
  api?: MyMemberApi;
};

export function ContactScreen({ api }: Props) {
  const { status, myMember, reload, save } = useOwnMember(api);

  if (status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="loading" message={membersText.own.loading} />
      </View>
    );
  }

  if (status === 'not-linked') {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="empty" message={membersText.own.notLinked} />
      </View>
    );
  }

  if (status === 'error' || myMember === null) {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView
          kind="error"
          message={membersText.own.loadError}
          retryLabel={membersText.retry}
          onRetry={() => void reload()}
        />
      </View>
    );
  }

  return <ContactForm myMember={myMember} save={save} />;
}

type FormProps = { myMember: MyMember; save: ReturnType<typeof useOwnMember>['save'] };

const FIELD_OF_CONSENT: Record<string, ContactFieldName> = {
  MOBILE: 'mobile',
  LANDLINE: 'landline',
  EMAIL: 'email',
};

function ContactForm({ myMember, save }: FormProps) {
  const { me } = useSession();
  const { member } = myMember;
  const [draft, setDraft] = useState<ContactDraft>(() => draftFrom(member));
  const [contactErrors, setContactErrors] = useState<ContactDraftErrors>({});
  const [problem, setProblem] = useState<MemberProblem | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const origins = myMember.currentConsents.filter((consent) => consent.channel !== 'APP');

  function fail(next: MemberProblem | null, errors: ContactDraftErrors = {}) {
    setProblem(next);
    setContactErrors(errors);
    setSaved(false);
    hapticFailure();
  }

  async function submit() {
    const errors = validateDraft(draft);
    if (Object.keys(errors).length > 0) {
      fail(null, errors);
      return;
    }
    setSubmitting(true);
    setProblem(null);
    setContactErrors({});
    setSaved(false);
    try {
      const result = await save(toContactInput(draft));
      if (result.ok) {
        setDraft(draftFrom(result.myMember.member));
        setSaved(true);
        hapticSuccess();
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
      <Text variant="title">{membersText.own.row}</Text>
      <ListSection>
        <ListRow
          title={fullNameOf(member)}
          subtitle={membersText.own.nameLabel}
          trailing={me ? communitiesText.option(me.communityNumber) : undefined}
        />
      </ListSection>
      <Text>{membersText.contact.notice}</Text>
      <Text>{membersText.own.hideNote}</Text>
      <ContactFields
        draft={draft}
        messages={messages}
        onChange={(field, next) => {
          setSaved(false);
          setDraft((current) => ({ ...current, [field]: next }));
        }}
      />
      {origins.length > 0 ? (
        <ListSection title={membersText.own.origin.title}>
          {origins.map((consent) => (
            <ListRow
              key={consent.field}
              title={membersText.contact.fields[FIELD_OF_CONSENT[consent.field]].label}
              subtitle={membersText.own.origin.line(consent.channel, consent.recordedBy.role, consent.recordedBy.name)}
            />
          ))}
        </ListSection>
      ) : null}
      {problem && problem.field === null ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem.message}`}
        </Text>
      ) : null}
      {saved ? <Text role="status">{membersText.own.saved}</Text> : null}
      <Button accessibilityLabel={membersText.contact.save} disabled={submitting} onPress={submit}>
        <Text>{submitting ? membersText.form.saving : membersText.contact.save}</Text>
      </Button>
    </FormScroll>
  );
}
