import { Stack, useRouter } from 'expo-router';
import { HeaderButton } from '@/components/navigation/header-button';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { navigationText } from '@/i18n/es/navigation';
import { navigationFor } from '@/lib/auth/navigation-permissions';
import { RoomOccupancyScreen } from '@/screens/rooms/room-occupancy';

export default function AgendaRoute() {
  const router = useRouter();
  const { user } = useCurrentUser();
  const { canCreateEvent } = navigationFor(user?.role);
  return (
    <>
      <Stack.Screen
        options={{
          headerRight: canCreateEvent
            ? () => (
                <HeaderButton
                  label={navigationText.newEvent}
                  visibleText="+"
                  onPress={() => router.push('/agenda/nuevo')}
                />
              )
            : undefined,
        }}
      />
      <RoomOccupancyScreen />
    </>
  );
}
