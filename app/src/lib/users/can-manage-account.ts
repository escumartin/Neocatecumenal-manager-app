import type { User } from '@/lib/api/users-api';

type Role = User['role'];

type Actor = { userId: string; role: Role };
type Target = { id: string; role: Role };

export function canManageAccount(me: Actor | null | undefined, target: Target): boolean {
  if (!me || me.userId === target.id) {
    return false;
  }
  if (me.role === 'ADMINISTRATOR') {
    return true;
  }
  return me.role === 'MODERATOR' && target.role === 'REGISTERED';
}
