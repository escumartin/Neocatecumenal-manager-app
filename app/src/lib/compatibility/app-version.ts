const PATTERN = /^(\d+)\.(\d+)\.(\d+)$/;

function parse(version: string): number[] | null {
  const match = PATTERN.exec(version);
  return match ? [Number(match[1]), Number(match[2]), Number(match[3])] : null;
}

export function isBelowMinimum(current: string, minimum: string): boolean {
  const currentParts = parse(current);
  const minimumParts = parse(minimum);
  if (!currentParts || !minimumParts) {
    return false;
  }
  for (let index = 0; index < currentParts.length; index += 1) {
    if (currentParts[index] !== minimumParts[index]) {
      return currentParts[index] < minimumParts[index];
    }
  }
  return false;
}
