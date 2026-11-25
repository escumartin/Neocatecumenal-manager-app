import { Stack } from 'expo-router';
import { navigationText } from '@/i18n/es/navigation';
import { detailStackOptions, formSheetOptions, tabStackOptions } from '@/lib/navigation/stack-options';

export const unstable_settings = { initialRouteName: 'index' };

export default function ReservasLayout() {
  return (
    <Stack screenOptions={tabStackOptions}>
      <Stack.Screen name="index" options={{ title: navigationText.tabs.reservas }} />
      <Stack.Screen
        name="pendientes"
        options={{
          ...detailStackOptions,
          title: navigationText.pendingRequests,
        }}
      />
      <Stack.Screen name="nueva" options={formSheetOptions(navigationText.newBooking)} />
    </Stack>
  );
}
