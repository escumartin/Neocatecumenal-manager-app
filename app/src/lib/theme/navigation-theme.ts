import { DarkTheme, DefaultTheme, type Theme } from 'expo-router';
import { themeColorsFor } from './colors';

const light: Theme = {
  ...DefaultTheme,
  dark: false,
  colors: { ...DefaultTheme.colors, ...themeColorsFor('light') },
};

const dark: Theme = {
  ...DarkTheme,
  dark: true,
  colors: { ...DarkTheme.colors, ...themeColorsFor('dark') },
};

export function navigationThemeFor(scheme: 'light' | 'dark' | null | undefined): Theme {
  return scheme === 'dark' ? dark : light;
}
