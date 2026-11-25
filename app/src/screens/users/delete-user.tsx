import { useState } from 'react';
import { View } from 'react-native';
import { ListRow } from '@/components/ui/list-row';
import { ListSection } from '@/components/ui/list-section';
import { Text } from '@/components/ui/text';
import { useUsers } from '@/hooks/users/use-users';
import { messageForUsersFailure, usersText } from '@/i18n/es/users';
import type { User, UsersApi } from '@/lib/api/users-api';
import { confirmDestructive } from '@/lib/confirm';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { DeleteConfirmation } from './delete-confirmation';

type Props = {
  user: User;
  api?: UsersApi;
  onDeleted: () => void;
};

export function DeleteUser({ user, api, onDeleted }: Props) {
  const { remove } = useUsers(null, api, false);
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function deleteUser() {
    setDeleting(true);
    try {
      const result = await remove(user.id);
      if (result.ok) {
        hapticSuccess();
        onDeleted();
        return;
      }
      hapticFailure();
      setMessage(messageForUsersFailure(result));
    } catch {
      hapticFailure();
      setMessage(usersText.errors.UNKNOWN);
    } finally {
      setDeleting(false);
    }
  }

  async function confirmDeletion() {
    await deleteUser();
    setConfirming(false);
  }

  function requestDeletion() {
    setMessage(null);
    const shownNatively = confirmDestructive({
      title: usersText.delete.title(user.name),
      message: usersText.delete.warning,
      confirmLabel: usersText.delete.confirm,
      cancelLabel: usersText.delete.cancel,
      onConfirm: () => void deleteUser(),
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
        <ListRow destructive disabled={deleting} title={usersText.delete.row} onPress={requestDeletion} />
      </ListSection>
      {confirming ? (
        <DeleteConfirmation
          name={user.name}
          busy={deleting}
          onConfirm={() => void confirmDeletion()}
          onCancel={() => setConfirming(false)}
        />
      ) : null}
    </View>
  );
}
