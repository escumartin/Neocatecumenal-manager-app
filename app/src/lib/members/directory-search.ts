import type { Member } from '@/lib/api/members-api';
import { fullNameOf } from '@/screens/members/member-name';

const COMBINING_MARKS = /\p{M}/gu;

export function normalizeForSearch(text: string): string {
  return text.normalize('NFD').replace(COMBINING_MARKS, '').toLowerCase().trim();
}

export function filterMembers(members: Member[], query: string): Member[] {
  const words = normalizeForSearch(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return members;
  }
  return members.filter((member) => {
    const name = normalizeForSearch(fullNameOf(member));
    return words.every((word) => name.includes(word));
  });
}
