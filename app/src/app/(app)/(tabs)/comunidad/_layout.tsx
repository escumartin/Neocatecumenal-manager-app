import { Stack } from 'expo-router';
import { navigationText } from '@/i18n/es/navigation';
import { detailStackOptions, tabStackOptions } from '@/lib/navigation/stack-options';

export const unstable_settings = { initialRouteName: 'index' };

export default function ComunidadLayout() {
  return (
    <Stack screenOptions={tabStackOptions}>
      <Stack.Screen name="index" options={{ title: navigationText.tabs.comunidad }} />
      <Stack.Screen name="[memberId]" options={{ ...detailStackOptions, title: navigationText.directoryMember }} />
    </Stack>
  );
}
