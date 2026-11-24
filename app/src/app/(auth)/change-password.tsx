import { useRouter } from 'expo-router';
import { ChangePassword } from '@/screens/auth/change-password';
import { PendingStepGate } from '@/screens/auth/pending-step-gate';

export default function ChangePasswordRoute() {
  const router = useRouter();
  return (
    <PendingStepGate screen="change-password">
      <ChangePassword onDone={() => router.replace('/')} onCancel={() => router.back()} />
    </PendingStepGate>
  );
}
