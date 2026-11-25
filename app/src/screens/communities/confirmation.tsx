import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';

type Props = {
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel: string;
  workingLabel: string;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function CommunityConfirmation({
  title,
  description,
  confirmLabel,
  cancelLabel,
  workingLabel,
  busy,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <AlertDialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) {
          onCancel();
        }
      }}
    >
      <AlertDialogContent accessibilityLabel={title}>
        <AlertDialogTitle>{title}</AlertDialogTitle>
        <AlertDialogDescription>{description}</AlertDialogDescription>
        <AlertDialogCancel asChild disabled={busy}>
          <Button variant="outline" accessibilityLabel={cancelLabel} disabled={busy}>
            <Text>{cancelLabel}</Text>
          </Button>
        </AlertDialogCancel>
        <Button variant="destructive" accessibilityLabel={confirmLabel} disabled={busy} onPress={onConfirm}>
          <Text>{busy ? workingLabel : confirmLabel}</Text>
        </Button>
      </AlertDialogContent>
    </AlertDialog>
  );
}
