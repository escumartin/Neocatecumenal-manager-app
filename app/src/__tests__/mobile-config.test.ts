const fs = jest.requireActual('fs');
const path = jest.requireActual('path');

const root: string = path.resolve('.');

function readJson(name: string) {
  return JSON.parse(fs.readFileSync(path.join(root, name), 'utf8'));
}

const identifier = 'es.parroquia.neocatecumenal';

describe('mobile configuration', () => {
  const expo = readJson('app.json').expo;
  const eas = readJson('eas.json');

  it('uses the same permanent identifier on iOS and Android', () => {
    expect(expo.ios.bundleIdentifier).toBe(identifier);
    expect(expo.android.package).toBe(identifier);
  });

  it('ties the runtime version to the app version', () => {
    expect(expo.runtimeVersion).toEqual({ policy: 'appVersion' });
  });

  it('declares the updates entry so that EAS Update can serve the app', () => {
    expect(expo.updates).toBeDefined();
    expect(expo.updates.url).toEqual(expect.stringMatching(/^https:\/\/u\.expo\.dev\//));
  });

  it('depends on expo-updates', () => {
    expect(readJson('package.json').dependencies['expo-updates']).toBeDefined();
  });

  it('serves the web as a single page', () => {
    expect(expo.web.output).toBe('single');
  });

  it('defines the development, preview and production build profiles', () => {
    expect(Object.keys(eas.build)).toEqual(expect.arrayContaining(['development', 'preview', 'production']));
  });

  it('publishes each profile to its own channel', () => {
    expect(eas.build.development.channel).toBe('development');
    expect(eas.build.preview.channel).toBe('preview');
    expect(eas.build.production.channel).toBe('production');
  });

  it('increments the build number automatically in production', () => {
    expect(eas.build.production.autoIncrement).toBe(true);
  });

  it('points the production build to the service over HTTPS', () => {
    expect(eas.build.production.env.EXPO_PUBLIC_API_URL).toMatch(/^https:\/\//);
  });

  it('holds no secrets', () => {
    const text = JSON.stringify(eas) + JSON.stringify(expo);

    expect(text).not.toMatch(/token|password|secret|-----BEGIN/i);
  });
});
