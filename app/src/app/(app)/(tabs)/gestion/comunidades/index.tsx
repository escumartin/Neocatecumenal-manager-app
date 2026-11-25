import { useRouter } from 'expo-router';
import { CommunitiesList } from '@/screens/communities/list';

export default function CommunitiesRoute() {
  const router = useRouter();
  return (
    <CommunitiesList
      onCreate={() => router.push('/gestion/comunidades/nueva')}
      onOpen={(communityId) =>
        router.push({
          pathname: '/gestion/comunidades/[communityId]',
          params: { communityId },
        })
      }
    />
  );
}
