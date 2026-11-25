import { useRouter } from 'expo-router';
import { Account } from '@/screens/account';

export default function AccountRoute() {
  const router = useRouter();
  return <Account onChangePassword={() => router.push('/change-password')} />;
}
