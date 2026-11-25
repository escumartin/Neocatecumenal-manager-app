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
import { bookingsText } from '@/i18n/es/bookings';

export type CancellableOccurrence = {
  id: string;
  date: string;
  startTime: string;
  endTime: string;
};

type Props = {
  roomName: string;
  occurrences?: CancellableOccurrence[];
  busy: boolean;
  problem?: string | null;
  onConfirm: (bookingId?: string) => void;
  onDismiss: () => void;
};

export function CancelConfirmation({
  roomName,
  occurrences,
  busy,
  problem,
  onConfirm,
  onDismiss,
}: Props) {
  const [chosen, setChosen] = useState<string | undefined>(undefined);
  const title = bookingsText.cancel.title(roomName);

  return (
    <AlertDialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) {
          onDismiss();
        }
      }}
    >
      <AlertDialogContent accessibilityLabel={title}>
        <AlertDialogTitle>{title}</AlertDialogTitle>
        <AlertDialogDescription>
          {bookingsText.cancel.warning}
        </AlertDialogDescription>
        {occurrences && occurrences.length > 0 ? (
          <View
            role="radiogroup"
            accessibilityLabel={bookingsText.cancel.choice}
            className="gap-3"
          >
            <Button
              role="radio"
              aria-checked={chosen === undefined}
              accessibilityState={{ checked: chosen === undefined }}
              variant={chosen === undefined ? 'default' : 'outline'}
              accessibilityLabel={bookingsText.cancel.wholeSeries}
              disabled={busy}
              onPress={() => setChosen(undefined)}
            >
              <Text>
                {chosen === undefined
                  ? `✓ ${bookingsText.cancel.wholeSeries}`
                  : bookingsText.cancel.wholeSeries}
              </Text>
            </Button>
            {occurrences.map((occurrence) => {
              const label = bookingsText.cancel.oneOccurrence(occurrence);
              return (
                <Button
                  key={occurrence.id}
                  role="radio"
                  aria-checked={chosen === occurrence.id}
                  accessibilityState={{ checked: chosen === occurrence.id }}
                  variant={chosen === occurrence.id ? 'default' : 'outline'}
                  accessibilityLabel={label}
                  disabled={busy}
                  onPress={() => setChosen(occurrence.id)}
                >
                  <Text>{chosen === occurrence.id ? `✓ ${label}` : label}</Text>
                </Button>
              );
            })}
          </View>
        ) : null}
        {problem ? (
          <Text
            role="alert"
            accessibilityLiveRegion="assertive"
            className="text-destructive"
          >
            {`⚠ ${problem}`}
          </Text>
        ) : null}
        <AlertDialogCancel asChild disabled={busy}>
          <Button
            variant="outline"
            accessibilityLabel={bookingsText.cancel.keep}
            disabled={busy}
          >
            <Text>{bookingsText.cancel.keep}</Text>
          </Button>
        </AlertDialogCancel>
        <Button
          variant="destructive"
          accessibilityLabel={bookingsText.cancel.confirm}
          disabled={busy}
          onPress={() => onConfirm(chosen)}
        >
          <Text>
            {busy
              ? bookingsText.cancel.cancelling
              : bookingsText.cancel.confirm}
          </Text>
        </Button>
      </AlertDialogContent>
    </AlertDialog>
  );
}
