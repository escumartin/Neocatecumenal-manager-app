import { Redirect, useRouter } from 'expo-router';
import { useCreatedUserHandoff } from '@/hooks/users/use-created-user-handoff';
import { UserCreated } from '@/screens/users/created';

export default function CreatedUserRoute() {
  const router = useRouter();
  const handoff = useCreatedUserHandoff();

  if (!handoff) {
    return <Redirect href="/users" />;
  }

  return <UserCreated created={handoff.created} variant={handoff.kind} onDone={() => router.replace('/users')} />;
}
