import { useState } from 'react';
import { View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { Text } from '@/components/ui/text';
import { useMemberLink } from '@/hooks/members/use-member-link';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { membersText, problemForMembersFailure } from '@/i18n/es/members';
import type { Member, MemberManagementApi } from '@/lib/api/members-api';
import { confirmDestructive } from '@/lib/confirm';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { CommunityConfirmation } from '@/screens/communities/confirmation';
import { fullNameOf } from './member-name';

type Props = {
  member: Member;
  api?: MemberManagementApi;
  onOpenLink?: (memberId: string) => void;
  onOpenTransfer?: (memberId: string) => void;
  onUnlinked: () => void;
};

export function MemberLinkActions({ member, api, onOpenLink, onOpenTransfer, onUnlinked }: Props) {
  const { isAdministrator } = useCurrentUser();
  const { unlink } = useMemberLink(member.id, api);
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const linked = member.linkedUserId !== undefined;
  const texts = membersText.link.unlink;

  async function run() {
    setWorking(true);
    setMessage(null);
    try {
      const result = await unlink();
      if (result.ok) {
        hapticSuccess();
        onUnlinked();
        return;
      }
      hapticFailure();
      setMessage(problemForMembersFailure(result).message);
    } catch {
      hapticFailure();
      setMessage(membersText.errors.UNKNOWN);
    } finally {
      setWorking(false);
      setConfirming(false);
    }
  }

  function request() {
    setMessage(null);
    const shownNatively = confirmDestructive({
      title: texts.title(fullNameOf(member)),
      message: texts.warning,
      confirmLabel: texts.confirm,
      cancelLabel: texts.cancel,
      onConfirm: () => void run(),
    });
    if (!shownNatively) {
      setConfirming(true);
    }
  }

  return (
    <View className="gap-3">
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      <ListSection>
        {linked ? (
          <ListRow title={texts.row} destructive disabled={working} onPress={request} />
        ) : (
          <ListRow title={membersText.link.row} onPress={() => onOpenLink?.(member.id)} />
        )}
        {isAdministrator ? (
          <ListRow title={membersText.transfer.row} onPress={() => onOpenTransfer?.(member.id)} />
        ) : null}
      </ListSection>
      {confirming ? (
        <CommunityConfirmation
          title={texts.title(fullNameOf(member))}
          description={texts.warning}
          confirmLabel={texts.confirm}
          cancelLabel={texts.cancel}
          workingLabel={texts.working}
          busy={working}
          onConfirm={() => void run()}
          onCancel={() => setConfirming(false)}
        />
      ) : null}
    </View>
  );
}
