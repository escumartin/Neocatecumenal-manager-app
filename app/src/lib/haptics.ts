import * as ExpoHaptics from 'expo-haptics';
import { Platform } from 'react-native';

function run(feedback: () => Promise<void>) {
  if (Platform.OS === 'web') {
    return;
  }
  try {
    feedback().catch(() => undefined);
  } catch {
    return;
  }
}

export function hapticSuccess() {
  run(() => ExpoHaptics.impactAsync(ExpoHaptics.ImpactFeedbackStyle.Light));
}

export function hapticFailure() {
  run(() => ExpoHaptics.notificationAsync(ExpoHaptics.NotificationFeedbackType.Error));
}
