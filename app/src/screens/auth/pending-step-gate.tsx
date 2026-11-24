import { Redirect } from 'expo-router';
import type { ReactNode } from 'react';
import { View } from 'react-native';
import { Text } from '@/components/ui/text';
import { useSession } from '@/hooks/session/use-session';
import { identity } from '@/i18n/es/identity';
import { routeForPendingSteps } from '@/lib/auth/pending-steps';
import { SessionUnavailable } from './session-unavailable';

type Props = {
  screen: 'privacy-notice' | 'change-password';
  children: ReactNode;
};

export function PendingStepGate({ screen, children }: Props) {
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

  if (status === 'anonymous' || !me) {
    return <Redirect href="/login" />;
  }

  const next = routeForPendingSteps(me.pendingSteps);

  if (screen === 'privacy-notice' && next !== '/privacy-notice') {
    return <Redirect href={next ?? '/'} />;
  }

  if (screen === 'change-password' && next === '/privacy-notice') {
    return <Redirect href="/privacy-notice" />;
  }

  return <>{children}</>;
}
