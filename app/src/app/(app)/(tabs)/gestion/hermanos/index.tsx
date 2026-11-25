import { useRouter } from 'expo-router';
import { MembersList } from '@/screens/members/list';

export default function MembersRoute() {
  const router = useRouter();
  return (
    <MembersList
      onCreate={(communityId) =>
        router.push({ pathname: '/gestion/hermanos/nuevo', params: communityId ? { communityId } : {} })
      }
      onOpen={(memberId) => router.push({ pathname: '/gestion/hermanos/[memberId]', params: { memberId } })}
    />
  );
}
