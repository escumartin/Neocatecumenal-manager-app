import { useLocalSearchParams } from 'expo-router';
import { MemberCardScreen } from '@/screens/directory/member-card';

export default function MemberCardRoute() {
  const { memberId } = useLocalSearchParams<{ memberId: string }>();
  return <MemberCardScreen memberId={memberId} />;
}
