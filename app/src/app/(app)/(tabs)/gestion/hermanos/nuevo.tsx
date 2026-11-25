import { useLocalSearchParams, useRouter } from 'expo-router';
import { MemberForm } from '@/screens/members/form';

export default function NewMemberRoute() {
  const router = useRouter();
  const { communityId } = useLocalSearchParams<{ communityId?: string }>();
  return <MemberForm communityId={communityId} onSaved={() => router.back()} />;
}
