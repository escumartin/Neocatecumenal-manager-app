import { Stack } from 'expo-router';
import { SessionGuard } from '@/screens/auth/session-guard';

export default function AppLayout() {
  return (
    <SessionGuard>
      <Stack screenOptions={{ headerShown: false }} />
    </SessionGuard>
  );
}
