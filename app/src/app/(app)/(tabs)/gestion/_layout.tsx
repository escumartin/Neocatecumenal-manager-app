import { Stack } from 'expo-router';
import { navigationText } from '@/i18n/es/navigation';
import { detailStackOptions, formSheetOptions, tabStackOptions } from '@/lib/navigation/stack-options';

export const unstable_settings = { initialRouteName: 'index' };

export default function GestionLayout() {
  return (
    <Stack screenOptions={tabStackOptions}>
      <Stack.Screen name="index" options={{ title: navigationText.tabs.gestion }} />
      <Stack.Screen
        name="salones"
        options={{
          ...detailStackOptions,
          title: navigationText.management.rooms,
        }}
      />
      <Stack.Screen
        name="usuarios/index"
        options={{
          ...detailStackOptions,
          title: navigationText.management.users,
        }}
      />
      <Stack.Screen
        name="comunidades/index"
        options={{
          ...detailStackOptions,
          title: navigationText.management.communities,
        }}
      />
      <Stack.Screen name="comunidades/nueva" options={formSheetOptions(navigationText.newCommunity)} />
      <Stack.Screen name="comunidades/[communityId]" options={formSheetOptions(navigationText.editCommunity)} />
      <Stack.Screen name="usuarios/nuevo" options={formSheetOptions(navigationText.newUser)} />
      <Stack.Screen name="usuarios/[userId]" options={formSheetOptions(navigationText.editUser)} />
      <Stack.Screen
        name="usuarios/creado"
        options={{
          ...formSheetOptions(navigationText.createdUser),
          headerLeft: undefined,
        }}
      />
    </Stack>
  );
}
