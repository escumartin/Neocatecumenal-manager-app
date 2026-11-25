import { Stack } from 'expo-router';
import { membersText } from '@/i18n/es/members';
import { navigationText } from '@/i18n/es/navigation';
import { detailStackOptions, tabStackOptions } from '@/lib/navigation/stack-options';

export const unstable_settings = { initialRouteName: 'index' };

export default function CuentaLayout() {
  return (
    <Stack screenOptions={tabStackOptions}>
      <Stack.Screen name="index" options={{ title: navigationText.tabs.cuenta }} />
      <Stack.Screen name="contacto" options={{ ...detailStackOptions, title: membersText.own.row }} />
    </Stack>
  );
}
