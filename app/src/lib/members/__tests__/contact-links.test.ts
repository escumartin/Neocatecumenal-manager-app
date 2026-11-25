import { mailLink, smsLink, telLink } from '../contact-links';

describe('contact links', () => {
  it('builds a tel link without spaces', () => {
    expect(telLink('600000001')).toBe('tel:600000001');
    expect(telLink('+351912345678')).toBe('tel:+351912345678');
  });

  it('builds an sms link', () => {
    expect(smsLink('600000001')).toBe('sms:600000001');
  });

  it('builds a mailto link', () => {
    expect(mailLink('ana@correo.es')).toBe('mailto:ana@correo.es');
  });
});
