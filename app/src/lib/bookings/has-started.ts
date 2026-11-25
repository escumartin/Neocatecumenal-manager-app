const madrid = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Europe/Madrid',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

function madridLocal(now: Date): string {
  const parts = Object.fromEntries(
    madrid.formatToParts(now).map((part) => [part.type, part.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

export function hasStarted(
  date: string,
  startTime: string,
  now: Date = new Date(),
): boolean {
  return `${date}T${startTime}` <= madridLocal(now);
}
