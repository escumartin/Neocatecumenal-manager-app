import { useLocalSearchParams, useRouter } from 'expo-router';
import { createdUserHandoff } from '@/lib/users/created-user-handoff';
import { UserEdit } from '@/screens/users/edit';

export default function EditUserRoute() {
  const router = useRouter();
  const { userId } = useLocalSearchParams<{ userId: string }>();
  return (
    <UserEdit
      userId={userId}
      onCreated={(created) => {
        createdUserHandoff.set(created);
        router.replace('/gestion/usuarios/creado');
      }}
      onPasswordReset={(reset) => {
        createdUserHandoff.set(reset, 'reset');
        router.replace('/gestion/usuarios/creado');
      }}
      onSaved={() => router.back()}
    />
  );
}
