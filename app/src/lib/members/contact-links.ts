export function telLink(number: string): string {
  return `tel:${number}`;
}

export function smsLink(number: string): string {
  return `sms:${number}`;
}

export function mailLink(address: string): string {
  return `mailto:${address}`;
}
