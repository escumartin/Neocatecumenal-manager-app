import { PendingStepGate } from '@/screens/auth/pending-step-gate';
import { PrivacyNotice } from '@/screens/auth/privacy-notice';

export default function PrivacyNoticeRoute() {
  return (
    <PendingStepGate screen="privacy-notice">
      <PrivacyNotice />
    </PendingStepGate>
  );
}
