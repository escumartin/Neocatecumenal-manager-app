import { Alert, Platform } from 'react-native';

type ConfirmOptions = {
  title: string;
  message?: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
};

export function confirmDestructive({ title, message, confirmLabel, cancelLabel, onConfirm }: ConfirmOptions): boolean {
  if (Platform.OS === 'web') {
    return false;
  }
  Alert.alert(title, message, [
    { text: cancelLabel, style: 'cancel' },
    { text: confirmLabel, style: 'destructive', onPress: onConfirm },
  ]);
  return true;
}
