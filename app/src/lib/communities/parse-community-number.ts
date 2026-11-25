export type ParsedCommunityNumber = { ok: true; value: number } | { ok: false; code: 'REQUIRED' | 'INVALID_FORMAT' };

const MAX_NUMBER = 2147483647;

export function parseCommunityNumber(typed: string): ParsedCommunityNumber {
  const trimmed = typed.trim();
  if (trimmed.length === 0) {
    return { ok: false, code: 'REQUIRED' };
  }
  if (!/^\d+$/.test(trimmed)) {
    return { ok: false, code: 'INVALID_FORMAT' };
  }
  const value = Number(trimmed);
  return value >= 1 && value <= MAX_NUMBER ? { ok: true, value } : { ok: false, code: 'INVALID_FORMAT' };
}
