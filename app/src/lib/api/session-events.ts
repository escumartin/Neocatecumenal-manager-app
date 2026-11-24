import type { PendingStepCode } from './client';

type Listener = {
  onUnauthorized: () => void;
  onPendingStep: (code: PendingStepCode) => void;
};

const listeners = new Set<Listener>();

export const sessionEvents = {
  subscribe(listener: Listener) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  unauthorized() {
    listeners.forEach((listener) => listener.onUnauthorized());
  },
  pendingStep(code: PendingStepCode) {
    listeners.forEach((listener) => listener.onPendingStep(code));
  },
};
