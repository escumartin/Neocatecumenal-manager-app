import { useRouter } from 'expo-router';
import { CreateEventScreen } from '@/screens/events/create-event';

export default function CreateEventRoute() {
  const router = useRouter();
  return (
    <CreateEventScreen
      onCreated={(event) => {
        router.dismiss();
        router.push(`/agenda/${event.id}`);
      }}
    />
  );
}
