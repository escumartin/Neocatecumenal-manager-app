export function fieldHint(...parts: (string | null | undefined)[]): string | undefined {
  const text = parts.filter((part): part is string => Boolean(part)).join(' ');
  return text.length > 0 ? text : undefined;
}
