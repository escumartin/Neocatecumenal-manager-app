import { useLocalSearchParams, useRouter } from 'expo-router';
import { CommunityEdit } from '@/screens/communities/edit';

export default function EditCommunityRoute() {
  const router = useRouter();
  const { communityId } = useLocalSearchParams<{ communityId: string }>();
  return (
    <CommunityEdit
      communityId={communityId}
      onSaved={() => router.back()}
      onDeleted={() => router.back()}
      onChooseResponsible={(id) =>
        router.push({ pathname: '/gestion/comunidades/responsable', params: { communityId: id } })
      }
    />
  );
}
