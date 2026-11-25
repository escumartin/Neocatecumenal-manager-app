import { useState } from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { FormScroll } from '@/components/ui/form-scroll';
import { RadioSection } from '@/components/ui/radio-section';
import { StateView } from '@/components/ui/state-view';
import { Text } from '@/components/ui/text';
import { useCommunities } from '@/hooks/communities/use-communities';
import { useMember } from '@/hooks/members/use-member';
import { useMemberTransfer } from '@/hooks/members/use-member-transfer';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { communitiesText } from '@/i18n/es/communities';
import { membersText, problemForMembersFailure } from '@/i18n/es/members';
import type { CommunitiesApi, Community } from '@/lib/api/communities-api';
import type { Member, MemberManagementApi } from '@/lib/api/members-api';
import { confirmDestructive } from '@/lib/confirm';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { CommunityConfirmation } from '@/screens/communities/confirmation';
import { fullNameOf } from './member-name';

type Props = {
  memberId: string;
  api?: MemberManagementApi;
  communitiesApi?: CommunitiesApi;
  onTransferred: () => void;
};

export function MemberTransfer({ memberId, api, communitiesApi, onTransferred }: Props) {
  const { status: sessionStatus, isAdministrator } = useCurrentUser();
  const { status, member, reload } = useMember(memberId, api);

  if (sessionStatus !== 'authenticated' || status === 'loading') {
    return (
      <View className="flex-1 bg-background p-6">
        <StateView kind="loading" message={membersText.form.loading} />
      </View>
    );
  }

  if (!isAdministrator) {
    return (
      <View className="flex-1 bg-background p-6">
        <Text>{membersText.transfer.administratorsOnly}</Text>
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

  return <TransferChoice member={member} api={api} communitiesApi={communitiesApi} onTransferred={onTransferred} />;
}

type ChoiceProps = {
  member: Member;
  api?: MemberManagementApi;
  communitiesApi?: CommunitiesApi;
  onTransferred: () => void;
};

function TransferChoice({ member, api, communitiesApi, onTransferred }: ChoiceProps) {
  const { status, communities, reload } = useCommunities(communitiesApi);
  const { transfer } = useMemberTransfer(member.id, api);
  const [chosen, setChosen] = useState<Community | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const targets = communities.filter((community) => community.id !== member.communityId && community.status !== 'DEACTIVATED');
  const options = targets.map((community) => ({ value: community.id, label: communitiesText.option(community.number) }));
  const name = fullNameOf(member);
  const dialog = membersText.transfer.dialog;

  function fail(text: string) {
    setMessage(text);
    hapticFailure();
  }

  async function run(target: Community) {
    setWorking(true);
    setMessage(null);
    try {
      const result = await transfer(target.id);
      if (result.ok) {
        hapticSuccess();
        onTransferred();
        return;
      }
      fail(problemForMembersFailure(result).message);
    } catch {
      fail(membersText.errors.UNKNOWN);
    } finally {
      setWorking(false);
      setConfirming(false);
    }
  }

  function request() {
    if (chosen === null) {
      fail(membersText.transfer.chooseCommunity);
      return;
    }
    setMessage(null);
    const shownNatively = confirmDestructive({
      title: dialog.title(name, chosen.number),
      message: dialog.warning,
      confirmLabel: dialog.confirm,
      cancelLabel: dialog.cancel,
      onConfirm: () => void run(chosen),
    });
    if (!shownNatively) {
      setConfirming(true);
    }
  }

  return (
    <FormScroll>
      <Text variant="title">{membersText.transfer.title}</Text>
      <Text variant="strong">{name}</Text>
      <Text>{membersText.transfer.hint}</Text>
      {status === 'idle' || status === 'loading' ? (
        <StateView kind="loading" message={communitiesText.loading} />
      ) : null}
      {status === 'error' ? (
        <StateView
          kind="error"
          message={communitiesText.loadError}
          retryLabel={communitiesText.retry}
          onRetry={() => void reload()}
        />
      ) : null}
      {status === 'ready' && options.length === 0 ? (
        <StateView kind="empty" message={membersText.transfer.empty} />
      ) : null}
      {options.length > 0 ? (
        <RadioSection
          title={membersText.transfer.communitiesTitle}
          options={options}
          value={chosen?.id ?? null}
          onChange={(id) => setChosen(targets.find((community) => community.id === id) ?? null)}
        />
      ) : null}
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      {options.length > 0 ? (
        <Button accessibilityLabel={membersText.transfer.confirm} disabled={working} onPress={request}>
          <Text>{working ? membersText.transfer.saving : membersText.transfer.confirm}</Text>
        </Button>
      ) : null}
      {confirming && chosen ? (
        <CommunityConfirmation
          title={dialog.title(name, chosen.number)}
          description={dialog.warning}
          confirmLabel={dialog.confirm}
          cancelLabel={dialog.cancel}
          workingLabel={dialog.working}
          busy={working}
          onConfirm={() => void run(chosen)}
          onCancel={() => setConfirming(false)}
        />
      ) : null}
    </FormScroll>
  );
}
