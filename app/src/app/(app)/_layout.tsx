import { Slot } from 'expo-router';
import { SessionGuard } from '@/screens/auth/session-guard';

export default function AppLayout() {
  return (
    <SessionGuard>
      <Slot />
    </SessionGuard>
  );
}
