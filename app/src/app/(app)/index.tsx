import { useRouter } from 'expo-router';
import { Home } from '@/screens/home';

export default function HomeRoute() {
  const router = useRouter();
  return <Home onOpenUsers={() => router.push('/users')} onPasswordChange={() => router.push('/change-password')} />;
}
