import type { Me } from '@/lib/api/session-api';
import type { PendingStepCode } from '@/lib/api/client';

export type PendingStep = Me['pendingSteps'][number];

export type PendingStepRoute = '/privacy-notice' | '/change-password';

export function routeForPendingSteps(steps: readonly PendingStep[]): PendingStepRoute | null {
  if (steps.includes('PRIVACY_NOTICE')) {
    return '/privacy-notice';
  }
  if (steps.includes('PASSWORD_CHANGE')) {
    return '/change-password';
  }
  return null;
}

export function stepForCode(code: PendingStepCode): PendingStep {
  return code === 'PASSWORD_CHANGE_REQUIRED' ? 'PASSWORD_CHANGE' : 'PRIVACY_NOTICE';
}
