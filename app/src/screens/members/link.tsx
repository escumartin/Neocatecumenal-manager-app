import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormScroll } from '@/components/ui/form-scroll';
import { RadioSection } from '@/components/ui/radio-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useMember } from '@/hooks/members/use-member';
import { useMemberLink } from '@/hooks/members/use-member-link';
import { useUsers } from '@/hooks/users/use-users';
import { membersText, problemForMembersFailure } from '@/i18n/es/members';
import type { Member, MemberManagementApi } from '@/lib/api/members-api';
import type { UsersApi } from '@/lib/api/users-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { fullNameOf } from './member-name';

type Props = {
  memberId: string;
  api?: MemberManagementApi;
  usersApi?: UsersApi;
  onLinked: () => void;
};

export function MemberLink({ memberId, api, usersApi, onLinked }: Props) {
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

  return <LinkChoice member={member} api={api} usersApi={usersApi} onLinked={onLinked} />;
}

type ChoiceProps = { member: Member; api?: MemberManagementApi; usersApi?: UsersApi; onLinked: () => void };

function LinkChoice({ member, api, usersApi, onLinked }: ChoiceProps) {
  const { status, users, reload } = useUsers(member.communityId, usersApi);
  const { link } = useMemberLink(member.id, api);
  const [chosen, setChosen] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const options = users
    .filter((user) => user.role === 'REGISTERED')
    .map((user) => ({ value: user.id, label: user.name }));

  function fail(text: string) {
    setMessage(text);
    hapticFailure();
  }

  async function submit() {
    if (chosen === null) {
      fail(membersText.link.chooseUser);
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      const result = await link(chosen);
      if (result.ok) {
        hapticSuccess();
        onLinked();
        return;
      }
      fail(problemForMembersFailure(result).message);
    } catch {
      fail(membersText.errors.UNKNOWN);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <FormScroll>
      <Text variant="title">{membersText.link.title}</Text>
      <Text variant="strong">{fullNameOf(member)}</Text>
      <Text>{membersText.link.hint}</Text>
      {status === 'idle' || status === 'loading' ? (
        <StateView kind="loading" message={membersText.link.loading} />
      ) : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={membersText.link.loadError}
          retryLabel={membersText.retry}
          onRetry={() => void reload()}
        />
      ) : null}
      {status === 'ready' && options.length === 0 ? (
        <StateView kind="empty" message={membersText.link.empty} />
      ) : null}
      {options.length > 0 ? (
        <RadioSection title={membersText.link.usersTitle} options={options} value={chosen} onChange={setChosen} />
      ) : null}
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      {status === 'ready' && options.length > 0 ? (
        <Button accessibilityLabel={membersText.link.confirm} disabled={submitting} onPress={submit}>
          <Text>{submitting ? membersText.link.saving : membersText.link.confirm}</Text>
        </Button>
      ) : null}
    </FormScroll>
  );
}
