import type { Member } from '@/lib/api/members-api';

export function fullNameOf(member: Pick<Member, 'givenName' | 'surnames'>): string {
  return [member.givenName, member.surnames].filter(Boolean).join(' ');
}
