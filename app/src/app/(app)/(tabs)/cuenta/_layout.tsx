import { Stack } from 'expo-router';
import { navigationText } from '@/i18n/es/navigation';
import { tabStackOptions } from '@/lib/navigation/stack-options';

export default function CuentaLayout() {
  return (
    <Stack screenOptions={tabStackOptions}>
      <Stack.Screen name="index" options={{ title: navigationText.tabs.cuenta }} />
    </Stack>
  );
}
