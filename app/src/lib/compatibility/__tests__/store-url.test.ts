import { storeUrlFor } from '../store-url';

const storeUrls = { ios: 'https://apps.example.test/ios', android: 'https://play.example.test/android' };

describe('storeUrlFor', () => {
  it('returns the iOS address on ios', () => {
    expect(storeUrlFor('ios', storeUrls)).toBe(storeUrls.ios);
  });

  it('returns the Android address on android', () => {
    expect(storeUrlFor('android', storeUrls)).toBe(storeUrls.android);
  });

  it('returns undefined when the platform has an empty address', () => {
    expect(storeUrlFor('ios', { ios: '', android: '' })).toBeUndefined();
  });

  it('returns undefined when the platform is missing', () => {
    expect(storeUrlFor('web', storeUrls)).toBeUndefined();
  });

  it('returns undefined when no addresses are configured', () => {
    expect(storeUrlFor('ios', undefined)).toBeUndefined();
  });
});
