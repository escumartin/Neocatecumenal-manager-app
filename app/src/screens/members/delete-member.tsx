import { useState } from 'react';
import { View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { Text } from '@/components/ui/text';
import { useMemberDelete } from '@/hooks/members/use-member-delete';
import { membersText, problemForMembersFailure } from '@/i18n/es/members';
import type { Member, MemberManagementApi } from '@/lib/api/members-api';
import { confirmDestructive } from '@/lib/confirm';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { CommunityConfirmation } from '@/screens/communities/confirmation';
import { fullNameOf } from './member-name';

type Props = {
  member: Member;
  api?: Pick<MemberManagementApi, 'remove'>;
  onDeleted: () => void;
};

export function MemberDeleteAction({ member, api, onDeleted }: Props) {
  const { remove } = useMemberDelete(member.id, api);
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const texts = membersText.delete;
  const warning = texts.warning(member.linkedUserId !== undefined);

  async function run() {
    setWorking(true);
    setMessage(null);
    try {
      const result = await remove();
      if (result.ok) {
        hapticSuccess();
        onDeleted();
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
      message: warning,
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
        <ListRow destructive disabled={working} title={texts.row} onPress={request} />
      </ListSection>
      {confirming ? (
        <CommunityConfirmation
          title={texts.title(fullNameOf(member))}
          description={warning}
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
