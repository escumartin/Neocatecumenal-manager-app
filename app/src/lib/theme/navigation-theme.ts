import { DarkTheme, DefaultTheme, type Theme } from 'expo-router';

const light: Theme = {
  ...DefaultTheme,
  dark: false,
  colors: {
    ...DefaultTheme.colors,
    background: '#FAF9F5',
    card: '#F5F0E8',
    text: '#141413',
    border: '#D9D1C3',
    primary: '#A9583E',
  },
};

const dark: Theme = {
  ...DarkTheme,
  dark: true,
  colors: {
    ...DarkTheme.colors,
    background: '#181715',
    card: '#1F1E1B',
    text: '#FAF9F5',
    border: '#3A3733',
    primary: '#E08A6B',
  },
};

export function navigationThemeFor(scheme: 'light' | 'dark' | null | undefined): Theme {
  return scheme === 'dark' ? dark : light;
}
