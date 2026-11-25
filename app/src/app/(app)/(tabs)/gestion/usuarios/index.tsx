import { useRouter } from 'expo-router';
import { UserList } from '@/screens/users/list';

export default function UsersRoute() {
  const router = useRouter();
  return (
    <UserList
      onCreate={() => router.push('/gestion/usuarios/nuevo')}
      onEdit={(userId) =>
        router.push({
          pathname: '/gestion/usuarios/[userId]',
          params: { userId },
        })
      }
    />
  );
}
