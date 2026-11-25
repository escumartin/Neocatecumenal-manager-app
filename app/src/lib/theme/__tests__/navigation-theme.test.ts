import { navigationThemeFor } from '../navigation-theme';

jest.mock('expo-router', () => ({
  DefaultTheme: { dark: false, colors: { notification: 'red' }, fonts: {} },
  DarkTheme: { dark: true, colors: { notification: 'red' }, fonts: {} },
}));

const { readFileSync } = jest.requireActual('fs');
const css: string = readFileSync('src/global.css', 'utf8');
const darkStart = css.indexOf('prefers-color-scheme: dark');
const lightCss = css.slice(0, darkStart === -1 ? undefined : darkStart);
const darkCss = darkStart === -1 ? '' : css.slice(darkStart);

function token(block: string, name: string) {
  const match = block.match(new RegExp(`--${name}:\\s*(#[0-9A-Fa-f]{6})`));
  return match?.[1].toLowerCase();
}

describe.each([
  ['light', lightCss],
  ['dark', darkCss],
] as const)('%s navigation theme', (scheme, block) => {
  it('mirrors the colour tokens of global.css', () => {
    const { colors, dark } = navigationThemeFor(scheme);

    expect(dark).toBe(scheme === 'dark');
    expect(String(colors.background).toLowerCase()).toBe(token(block, 'background'));
    expect(String(colors.card).toLowerCase()).toBe(token(block, 'card'));
    expect(String(colors.text).toLowerCase()).toBe(token(block, 'foreground'));
    expect(String(colors.border).toLowerCase()).toBe(token(block, 'border'));
    expect(String(colors.primary).toLowerCase()).toBe(token(block, 'primary'));
  });
});

describe('navigation theme selection', () => {
  it('falls back to light without a colour scheme', () => {
    expect(navigationThemeFor(null).dark).toBe(false);
    expect(navigationThemeFor(undefined).dark).toBe(false);
  });
});
