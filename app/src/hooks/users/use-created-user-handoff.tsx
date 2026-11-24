import { useState } from 'react';
import { createdUserHandoff } from '@/lib/users/created-user-handoff';

export function useCreatedUserHandoff() {
  const [handoff] = useState(() => createdUserHandoff.take());
  return handoff;
}
