import { useLocalSearchParams, useRouter } from 'expo-router';
import { MemberContactEdit } from '@/screens/members/contact-edit';

export default function MemberContactRoute() {
  const router = useRouter();
  const { memberId } = useLocalSearchParams<{ memberId: string }>();
  return <MemberContactEdit memberId={memberId} onSaved={() => router.back()} />;
}
