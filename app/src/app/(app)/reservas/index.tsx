import { useRouter } from 'expo-router';
import { MyRequestsScreen } from '@/screens/bookings/my-requests';

export default function BookingRequestsRoute() {
  const router = useRouter();
  return <MyRequestsScreen onRequest={() => router.push('/reservas/nueva')} />;
}
