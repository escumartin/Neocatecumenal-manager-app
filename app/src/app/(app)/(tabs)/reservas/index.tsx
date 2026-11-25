import { Stack, useRouter } from 'expo-router';
import { HeaderButton } from '@/components/navigation/header-button';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { navigationText } from '@/i18n/es/navigation';
import { navigationFor } from '@/lib/auth/navigation-permissions';
import { MyRequestsScreen } from '@/screens/bookings/my-requests';

export default function BookingRequestsRoute() {
  const router = useRouter();
  const { user } = useCurrentUser();
  const { canRequestBooking, canReviewPending } = navigationFor(user?.role);
  return (
    <>
      <Stack.Screen
        options={{
          headerLeft: canReviewPending
            ? () => (
                <HeaderButton
                  label={navigationText.pendingRequests}
                  onPress={() => router.push('/reservas/pendientes')}
                />
              )
            : undefined,
          headerRight: canRequestBooking
            ? () => (
                <HeaderButton
                  label={navigationText.newBooking}
                  visibleText="+"
                  onPress={() => router.push('/reservas/nueva')}
                />
              )
            : undefined,
        }}
      />
      <MyRequestsScreen onRequest={() => router.push('/reservas/nueva')} />
    </>
  );
}
