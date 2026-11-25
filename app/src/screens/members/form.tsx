import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormField } from '@/components/ui/form-field';
import { FormScroll } from '@/components/ui/form-scroll';
import { Input } from '@/components/ui/input';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useMember } from '@/hooks/members/use-member';
import { useMemberSave } from '@/hooks/members/use-member-save';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { membersText, problemForMembersFailure, type MemberProblem } from '@/i18n/es/members';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import type { Member, MemberInput, MemberManagementApi } from '@/lib/api/members-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import {
  draftFrom,
  hasSharedChanges,
  toContactInput,
  validateDraft,
  type ContactDraft,
  type ContactDraftErrors,
} from '@/lib/members/contact-form';
import { CommunityPicker } from '@/screens/users/community-picker';
import { ConsentSection, type ConsentChannelChoice } from './consent-section';
import { ContactFields, contactMessages } from './contact-fields';

type Props = {
  memberId?: string;
  communityId?: string;
  api?: MemberManagementApi;
  communitiesApi?: CommunitiesApi;
  onSaved: () => void;
  onOpenContact?: (memberId: string) => void;
};

export function MemberForm({ memberId, communityId, api, communitiesApi, onSaved, onOpenContact }: Props) {
  if (memberId) {
    return <ExistingMember memberId={memberId} api={api} onSaved={onSaved} onOpenContact={onOpenContact} />;
  }
  return <MemberFields initial={null} communityId={communityId} api={api} communitiesApi={communitiesApi} onSaved={onSaved} />;
}

function ExistingMember({
  memberId,
  api,
  onSaved,
  onOpenContact,
}: {
  memberId: string;
  api?: MemberManagementApi;
  onSaved: () => void;
  onOpenContact?: (memberId: string) => void;
}) {
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

  return <MemberFields initial={member} api={api} onSaved={onSaved} onOpenContact={onOpenContact} />;
}

type FieldsProps = {
  initial: Member | null;
  communityId?: string;
  api?: MemberManagementApi;
  communitiesApi?: CommunitiesApi;
  onSaved: () => void;
  onOpenContact?: (memberId: string) => void;
};

function MemberFields({ initial, communityId, api, communitiesApi, onSaved, onOpenContact }: FieldsProps) {
  const { save } = useMemberSave(initial?.id, api);
  const { user, isAdministrator } = useCurrentUser();
  const editing = initial !== null;
  const [givenName, setGivenName] = useState(initial?.givenName ?? '');
  const [surnames, setSurnames] = useState(initial?.surnames ?? '');
  const [chosenCommunity, setChosenCommunity] = useState<string | null>(communityId ?? null);
  const [draft, setDraft] = useState<ContactDraft>(draftFrom(undefined));
  const [contactErrors, setContactErrors] = useState<ContactDraftErrors>({});
  const [channel, setChannel] = useState<ConsentChannelChoice | null>(null);
  const [noticeGiven, setNoticeGiven] = useState(false);
  const [problem, setProblem] = useState<MemberProblem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const anyShared = !editing && hasSharedChanges(draftFrom(undefined), draft);

  function fail(next: MemberProblem | null, errors: ContactDraftErrors = {}) {
    setProblem(next);
    setContactErrors(errors);
    hapticFailure();
  }

  async function submit() {
    const trimmedGiven = givenName.trim();
    if (trimmedGiven.length === 0) {
      fail({ field: 'givenName', message: membersText.fieldErrors.givenName.REQUIRED });
      return;
    }
    const targetCommunity = editing ? initial.communityId : isAdministrator ? chosenCommunity : user?.communityId;
    if (!targetCommunity) {
      fail({ field: null, message: membersText.errors.COMMUNITY_REQUIRED });
      return;
    }
    const errors = anyShared ? validateDraft(draft) : {};
    if (Object.keys(errors).length > 0) {
      fail(null, errors);
      return;
    }
    if (anyShared && channel === null) {
      fail({ field: null, message: membersText.errors.CONSENT_REQUIRED });
      return;
    }
    if (anyShared && !noticeGiven) {
      fail({ field: null, message: membersText.errors.NOTICE_NOT_GIVEN });
      return;
    }
    const trimmedSurnames = surnames.trim();
    const input: MemberInput = {
      communityId: targetCommunity,
      givenName: trimmedGiven,
      ...(trimmedSurnames ? { surnames: trimmedSurnames } : {}),
      ...(anyShared && channel
        ? { contact: toContactInput(draft), onBehalfConsent: { channel, noticeGiven } }
        : {}),
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

  const givenNameProblem = problem?.field === 'givenName' ? problem.message : undefined;
  const surnamesProblem = problem?.field === 'surnames' ? problem.message : undefined;
  const messages = contactMessages(contactErrors);
  if (problem?.field === 'mobile' || problem?.field === 'landline' || problem?.field === 'email') {
    messages[problem.field] = problem.message;
  }

  return (
    <FormScroll>
      <Text variant="title">{editing ? membersText.form.editTitle : membersText.form.createTitle}</Text>
      {!editing && isAdministrator ? (
        <CommunityPicker selectedId={chosenCommunity} onSelect={setChosenCommunity} api={communitiesApi} />
      ) : null}
      <ListSection title={membersText.form.givenName}>
        <FormField description={membersText.form.givenNameHint}>
          <Input
            accessibilityLabel={membersText.form.givenName}
            accessibilityHint={givenNameProblem}
            invalid={givenNameProblem !== undefined}
            maxLength={100}
            returnKeyType="next"
            value={givenName}
            onChangeText={setGivenName}
          />
          {givenNameProblem ? (
            <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
              {`⚠ ${givenNameProblem}`}
            </Text>
          ) : null}
        </FormField>
      </ListSection>
      <ListSection title={membersText.form.surnames}>
        <FormField description={membersText.form.surnamesHint}>
          <Input
            accessibilityLabel={membersText.form.surnames}
            accessibilityHint={surnamesProblem}
            invalid={surnamesProblem !== undefined}
            maxLength={100}
            returnKeyType="done"
            value={surnames}
            onChangeText={setSurnames}
          />
          {surnamesProblem ? (
            <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
              {`⚠ ${surnamesProblem}`}
            </Text>
          ) : null}
        </FormField>
      </ListSection>
      {editing ? (
        <ListSection>
          <ListRow
            title={membersText.form.contactRow}
            icon="person.crop.rectangle"
            onPress={() => onOpenContact?.(initial.id)}
          />
        </ListSection>
      ) : (
        <>
          <Text>{membersText.contact.notice}</Text>
          <ContactFields
            draft={draft}
            messages={messages}
            onChange={(field, next) => setDraft((current) => ({ ...current, [field]: next }))}
          />
          {anyShared ? (
            <ConsentSection
              channel={channel}
              noticeGiven={noticeGiven}
              onChannel={setChannel}
              onNoticeGiven={setNoticeGiven}
            />
          ) : null}
        </>
      )}
      {problem && problem.field === null ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${problem.message}`}
        </Text>
      ) : null}
      <Button
        accessibilityLabel={editing ? membersText.form.save : membersText.form.create}
        disabled={submitting}
        onPress={submit}
      >
        <Text>{submitting ? membersText.form.saving : editing ? membersText.form.save : membersText.form.create}</Text>
      </Button>
    </FormScroll>
  );
}
