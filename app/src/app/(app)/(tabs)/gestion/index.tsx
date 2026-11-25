import { useRouter } from 'expo-router';
import { ManagementMenu } from '@/screens/management';

export default function ManagementRoute() {
  const router = useRouter();
  return (
    <ManagementMenu
      onOpenUsers={() => router.push('/gestion/usuarios')}
      onOpenRooms={() => router.push('/gestion/salones')}
    />
  );
}
