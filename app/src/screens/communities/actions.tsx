import { useState } from 'react';
import { View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { Text } from '@/components/ui/text';
import { communitiesText, messageForCommunitiesFailure } from '@/i18n/es/communities';
import type { CommunityDetail, CommunityOutcome } from '@/lib/api/communities-api';
import { confirmDestructive } from '@/lib/confirm';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { CommunityConfirmation } from './confirmation';

type Pending = 'deactivate' | 'delete';

type Props = {
  community: CommunityDetail;
  deactivate: () => Promise<CommunityOutcome>;
  reactivate: () => Promise<CommunityOutcome>;
  remove: () => Promise<CommunityOutcome>;
  onDeleted: () => void;
};

export function CommunityActions({ community, deactivate, reactivate, remove, onDeleted }: Props) {
  const [pending, setPending] = useState<Pending | null>(null);
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const texts = communitiesText.actions;

  async function run(action: () => Promise<CommunityOutcome>, afterSuccess?: () => void) {
    setWorking(true);
    setMessage(null);
    try {
      const result = await action();
      if (result.ok) {
        hapticSuccess();
        afterSuccess?.();
      } else {
        hapticFailure();
        setMessage(messageForCommunitiesFailure(result));
      }
    } catch {
      hapticFailure();
      setMessage(communitiesText.errors.UNKNOWN);
    } finally {
      setWorking(false);
      setPending(null);
    }
  }

  function request(kind: Pending) {
    setMessage(null);
    const dialog = kind === 'deactivate' ? texts.deactivate : texts.delete;
    const execute = () => void (kind === 'deactivate' ? run(deactivate) : run(remove, onDeleted));
    const shownNatively = confirmDestructive({
      title: dialog.title(community.number),
      message: dialog.warning,
      confirmLabel: dialog.confirm,
      cancelLabel: dialog.cancel,
      onConfirm: execute,
    });
    if (!shownNatively) {
      setPending(kind);
    }
  }

  function confirmPending() {
    if (pending === 'deactivate') {
      void run(deactivate);
    } else if (pending === 'delete') {
      void run(remove, onDeleted);
    }
  }

  const dialog = pending === 'deactivate' ? texts.deactivate : texts.delete;

  return (
    <View className="gap-3">
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      <ListSection>
        {community.status === 'DEACTIVATED' ? (
          <ListRow
            title={texts.reactivate.row}
            accessory="none"
            disabled={working}
            onPress={() => void run(reactivate)}
          />
        ) : (
          <ListRow
            title={texts.deactivate.row}
            accessory="none"
            disabled={working}
            onPress={() => request('deactivate')}
          />
        )}
        <ListRow destructive title={texts.delete.row} disabled={working} onPress={() => request('delete')} />
      </ListSection>
      {pending ? (
        <CommunityConfirmation
          title={dialog.title(community.number)}
          description={dialog.warning}
          confirmLabel={dialog.confirm}
          cancelLabel={dialog.cancel}
          workingLabel={dialog.working}
          busy={working}
          onConfirm={confirmPending}
          onCancel={() => setPending(null)}
        />
      ) : null}
    </View>
  );
}
