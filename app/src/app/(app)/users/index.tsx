import { useRouter } from 'expo-router';
import { UserList } from '@/screens/users/list';

export default function UsersRoute() {
  const router = useRouter();
  return (
    <UserList
      onCreate={() => router.push('/users/new')}
      onEdit={(userId) => router.push({ pathname: '/users/[userId]', params: { userId } })}
    />
  );
}
