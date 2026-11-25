import { useLocalSearchParams, useRouter } from 'expo-router';
import { MemberTransfer } from '@/screens/members/transfer';

export default function TransferMemberRoute() {
  const router = useRouter();
  const { memberId } = useLocalSearchParams<{ memberId: string }>();
  return <MemberTransfer memberId={memberId} onTransferred={() => router.back()} />;
}
