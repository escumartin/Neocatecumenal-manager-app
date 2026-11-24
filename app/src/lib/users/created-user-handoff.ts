import type { CreatedUser } from '@/lib/api/users-api';

export type HandoffKind = 'created' | 'reset';

let pending: CreatedUser | null = null;
let pendingKind: HandoffKind = 'created';

export const createdUserHandoff = {
  set(created: CreatedUser, kind: HandoffKind = 'created') {
    pending = created;
    pendingKind = kind;
  },
  peek() {
    return pending;
  },
  kind() {
    return pendingKind;
  },
  take(): { created: CreatedUser; kind: HandoffKind } | null {
    if (!pending) {
      return null;
    }
    const taken = { created: pending, kind: pendingKind };
    pending = null;
    pendingKind = 'created';
    return taken;
  },
  clear() {
    pending = null;
    pendingKind = 'created';
  },
};
