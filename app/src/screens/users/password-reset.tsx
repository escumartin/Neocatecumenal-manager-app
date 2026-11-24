import { useState } from 'react';
import { View } from 'react-native';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { messageForUsersFailure, usersText } from '@/i18n/es/users';
import { usersApi } from '@/lib/api';
import type { CreatedUser, User, UsersApi } from '@/lib/api/users-api';

type Props = {
  user: User;
  api?: UsersApi;
  onReset: (created: CreatedUser) => void;
};

export function PasswordReset({ user, api = usersApi, onReset }: Props) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setMessage(null);
    try {
      const result = await api.resetPassword(user.id);
      if (result.ok) {
        onReset({ ...user, initialPassword: result.initialPassword });
        return;
      }
      setConfirming(false);
      setMessage(messageForUsersFailure(result));
    } catch {
      setConfirming(false);
      setMessage(usersText.errors.UNKNOWN);
    } finally {
      setBusy(false);
    }
  }

  return (
    <View className="gap-3">
      {message ? (
        <Text role="alert" accessibilityLiveRegion="assertive" className="text-destructive">
          {`⚠ ${message}`}
        </Text>
      ) : null}
      <Button variant="outline" accessibilityLabel={usersText.reset.action} onPress={() => setConfirming(true)}>
        <Text>{usersText.reset.action}</Text>
      </Button>
      <AlertDialog
        open={confirming}
        onOpenChange={(open) => {
          if (!busy) {
            setConfirming(open);
          }
        }}
      >
        <AlertDialogContent accessibilityLabel={usersText.reset.confirmTitle(user.name)}>
          <AlertDialogTitle>{usersText.reset.confirmTitle(user.name)}</AlertDialogTitle>
          <AlertDialogDescription>{usersText.reset.warning}</AlertDialogDescription>
          <AlertDialogCancel asChild disabled={busy}>
            <Button variant="outline" accessibilityLabel={usersText.reset.cancel} disabled={busy}>
              <Text>{usersText.reset.cancel}</Text>
            </Button>
          </AlertDialogCancel>
          <Button
            variant="destructive"
            accessibilityLabel={usersText.reset.confirm}
            disabled={busy}
            onPress={() => void confirm()}
          >
            <Text>{busy ? usersText.reset.resetting : usersText.reset.confirm}</Text>
          </Button>
        </AlertDialogContent>
      </AlertDialog>
    </View>
  );
}
