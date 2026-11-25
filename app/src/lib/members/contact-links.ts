export function telLink(number: string): string {
  return `tel:${number}`;
}

export function smsLink(number: string): string {
  return `sms:${number}`;
}

export function mailLink(address: string): string {
  const at = address.lastIndexOf('@');
  if (at < 0) {
    return `mailto:${encodeURIComponent(address)}`;
  }
  return `mailto:${encodeURIComponent(address.slice(0, at))}@${encodeURIComponent(address.slice(at + 1))}`;
}
