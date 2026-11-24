import { AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogTitle } from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { usersText } from '@/i18n/es/users';

type Props = {
  name: string;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function DeleteConfirmation({ name, busy, onConfirm, onCancel }: Props) {
  return (
    <AlertDialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) {
          onCancel();
        }
      }}
    >
      <AlertDialogContent accessibilityLabel={usersText.delete.title(name)}>
        <AlertDialogTitle>{usersText.delete.title(name)}</AlertDialogTitle>
        <AlertDialogDescription>{usersText.delete.warning}</AlertDialogDescription>
        <AlertDialogCancel asChild disabled={busy}>
          <Button variant="outline" accessibilityLabel={usersText.delete.cancel} disabled={busy}>
            <Text>{usersText.delete.cancel}</Text>
          </Button>
        </AlertDialogCancel>
        <Button
          variant="destructive"
          accessibilityLabel={usersText.delete.confirm}
          disabled={busy}
          onPress={onConfirm}
        >
          <Text>{busy ? usersText.delete.deleting : usersText.delete.confirm}</Text>
        </Button>
      </AlertDialogContent>
    </AlertDialog>
  );
}
