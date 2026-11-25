import { mailLink, smsLink, telLink } from '../contact-links';

describe('contact links', () => {
  it('builds a tel link without spaces', () => {
    expect(telLink('600000001')).toBe('tel:600000001');
    expect(telLink('+351600000003')).toBe('tel:+351600000003');
  });

  it('builds an sms link', () => {
    expect(smsLink('600000001')).toBe('sms:600000001');
  });

  it('builds a mailto link', () => {
    expect(mailLink('ana@example.com')).toBe('mailto:ana@example.com');
  });

  it('encodes the characters of the local part that could add mail headers', () => {
    expect(mailLink('a?cc=x&bcc=y@example.com')).toBe('mailto:a%3Fcc%3Dx%26bcc%3Dy@example.com');
  });

  it('encodes a plus sign in the local part', () => {
    expect(mailLink('ana.perez+club@example.com')).toBe('mailto:ana.perez%2Bclub@example.com');
  });

  it('encodes the domain so it cannot add a query', () => {
    expect(mailLink('ana@example.com?bcc=x')).toBe('mailto:ana@example.com%3Fbcc%3Dx');
  });
});
