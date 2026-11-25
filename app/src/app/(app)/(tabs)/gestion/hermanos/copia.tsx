import { useLocalSearchParams } from 'expo-router';
import { MemberDataExportScreen } from '@/screens/members/data-export';

export default function MemberDataExportRoute() {
  const { memberId } = useLocalSearchParams<{ memberId: string }>();
  return <MemberDataExportScreen memberId={memberId} />;
}
