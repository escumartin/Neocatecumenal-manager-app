import { useRouter } from 'expo-router';
import { createdUserHandoff } from '@/lib/users/created-user-handoff';
import { UserEdit } from '@/screens/users/edit';

export default function NewUserRoute() {
  const router = useRouter();
  return (
    <UserEdit
      onCreated={(created) => {
        createdUserHandoff.set(created);
        router.replace('/gestion/usuarios/creado');
      }}
      onSaved={() => router.back()}
    />
  );
}
