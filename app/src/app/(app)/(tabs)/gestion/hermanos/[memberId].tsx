import { useLocalSearchParams, useRouter } from 'expo-router';
import { MemberForm } from '@/screens/members/form';

export default function EditMemberRoute() {
  const router = useRouter();
  const { memberId } = useLocalSearchParams<{ memberId: string }>();
  return (
    <MemberForm
      memberId={memberId}
      onSaved={() => router.back()}
      onOpenContact={(id) => router.push({ pathname: '/gestion/hermanos/contacto', params: { memberId: id } })}
      onOpenLink={(id) => router.push({ pathname: '/gestion/hermanos/vincular', params: { memberId: id } })}
      onOpenExport={(id) => router.push({ pathname: '/gestion/hermanos/copia', params: { memberId: id } })}
      onDeleted={() => router.back()}
      onOpenTransfer={(id) => router.push({ pathname: '/gestion/hermanos/trasladar', params: { memberId: id } })}
    />
  );
}
