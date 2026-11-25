export function addYears(date: Date, years: number): Date {
  const year = date.getFullYear() + years;
  const lastDay = new Date(year, date.getMonth() + 1, 0).getDate();
  return new Date(year, date.getMonth(), Math.min(date.getDate(), lastDay));
}
