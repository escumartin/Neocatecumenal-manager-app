import { useLocalSearchParams, useRouter } from 'expo-router';
import { MemberLink } from '@/screens/members/link';

export default function LinkMemberRoute() {
  const router = useRouter();
  const { memberId } = useLocalSearchParams<{ memberId: string }>();
  return <MemberLink memberId={memberId} onLinked={() => router.back()} />;
}
