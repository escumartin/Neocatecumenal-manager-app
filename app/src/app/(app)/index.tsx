import { Redirect } from 'expo-router';
import { useCurrentUser } from '@/hooks/session/use-current-user';
import { navigationFor } from '@/lib/auth/navigation-permissions';

export default function AppIndexRoute() {
  const { user } = useCurrentUser();
  return <Redirect href={`/${navigationFor(user?.role).firstTab}`} />;
}
