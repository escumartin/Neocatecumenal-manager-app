import { Stack } from 'expo-router';
import { navigationText } from '@/i18n/es/navigation';
import { detailStackOptions, formSheetOptions, tabStackOptions } from '@/lib/navigation/stack-options';

export const unstable_settings = { initialRouteName: 'index' };

export default function AgendaLayout() {
  return (
    <Stack screenOptions={tabStackOptions}>
      <Stack.Screen name="index" options={{ title: navigationText.tabs.agenda }} />
      <Stack.Screen name="[eventId]" options={{ ...detailStackOptions, title: navigationText.event }} />
      <Stack.Screen name="nuevo" options={formSheetOptions(navigationText.newEvent)} />
    </Stack>
  );
}
