import { Redirect } from 'expo-router';
import type { ReactNode } from 'react';
import { View } from 'react-native';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { identity } from '@/i18n/es/identity';
import { routeForPendingSteps } from '@/lib/auth/pending-steps';
import { SessionUnavailable } from './session-unavailable';

export function SessionGuard({ children }: { children: ReactNode }) {
  const { status, me } = useSession();

  if (status === 'loading') {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <Text role="status">{identity.loading}</Text>
      </View>
    );
  }

  if (status === 'error') {
    return <SessionUnavailable />;
  }

  if (status === 'anonymous') {
    return <Redirect href="/login" />;
  }

  const pendingRoute = me ? routeForPendingSteps(me.pendingSteps) : null;
  if (pendingRoute) {
    return <Redirect href={pendingRoute} />;
  }

  return <View className="flex-1 bg-background">{children}</View>;
}
