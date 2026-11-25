import { useLocalSearchParams, useRouter } from 'expo-router';
import { CommunityResponsible } from '@/screens/communities/responsible';

export default function CommunityResponsibleRoute() {
  const router = useRouter();
  const { communityId } = useLocalSearchParams<{ communityId: string }>();
  return <CommunityResponsible communityId={communityId} onSaved={() => router.back()} />;
}
