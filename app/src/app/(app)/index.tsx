import { useRouter } from 'expo-router';
import { Home } from '@/screens/home';

export default function HomeRoute() {
  const router = useRouter();
  return (
    <Home
      onOpenUsers={() => router.push('/users')}
      onOpenRooms={() => router.push('/salones')}
      onRequestBooking={() => router.push('/reservas/nueva')}
      onOpenMyRequests={() => router.push('/reservas')}
      onOpenOccupancy={() => router.push('/salones/ocupacion')}
      onOpenPendingRequests={() => router.push('/reservas/pendientes')}
      onCreateEvent={() => router.push('/eventos/nuevo')}
      onPasswordChange={() => router.push('/change-password')}
    />
  );
}
