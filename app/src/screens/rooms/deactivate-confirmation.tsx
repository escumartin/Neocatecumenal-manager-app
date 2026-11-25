import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';
import { roomsText } from '@/i18n/es/rooms';

type Props = {
  name: string;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function DeactivateConfirmation({ name, busy, onConfirm, onCancel }: Props) {
  return (
    <AlertDialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) {
          onCancel();
        }
      }}
    >
      <AlertDialogContent accessibilityLabel={roomsText.deactivate.title(name)}>
        <AlertDialogTitle>{roomsText.deactivate.title(name)}</AlertDialogTitle>
        <AlertDialogDescription>{roomsText.deactivate.warning}</AlertDialogDescription>
        <AlertDialogCancel asChild disabled={busy}>
          <Button variant="outline" accessibilityLabel={roomsText.deactivate.cancel} disabled={busy}>
            <Text>{roomsText.deactivate.cancel}</Text>
          </Button>
        </AlertDialogCancel>
        <Button
          variant="destructive"
          accessibilityLabel={roomsText.deactivate.confirm}
          disabled={busy}
          onPress={onConfirm}
        >
          <Text>{busy ? roomsText.deactivate.deactivating : roomsText.deactivate.confirm}</Text>
        </Button>
      </AlertDialogContent>
    </AlertDialog>
  );
}
