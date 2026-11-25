import { useRouter } from 'expo-router';
import { RequestBookingScreen } from '@/screens/bookings/request-booking';

export default function RequestBookingRoute() {
  const router = useRouter();
  return <RequestBookingScreen onCreated={() => router.replace('/reservas')} />;
}
