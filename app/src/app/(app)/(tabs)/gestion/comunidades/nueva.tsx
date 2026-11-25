import { useRouter } from 'expo-router';
import { CommunityEdit } from '@/screens/communities/edit';

export default function NewCommunityRoute() {
  const router = useRouter();
  return <CommunityEdit onSaved={() => router.back()} />;
}
