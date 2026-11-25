import { useRouter } from 'expo-router';
import { DirectoryScreen } from '@/screens/directory';

export default function DirectoryRoute() {
  const router = useRouter();
  return (
    <DirectoryScreen
      onOpen={(memberId) => router.push({ pathname: '/comunidad/[memberId]', params: { memberId } })}
    />
  );
}
