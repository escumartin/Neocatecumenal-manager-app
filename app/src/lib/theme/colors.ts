export type ThemeColors = {
  background: string;
  card: string;
  text: string;
  border: string;
  primary: string;
};

const light: ThemeColors = {
  background: '#FAF9F5',
  card: '#F5F0E8',
  text: '#141413',
  border: '#D9D1C3',
  primary: '#A9583E',
};

const dark: ThemeColors = {
  background: '#181715',
  card: '#1F1E1B',
  text: '#FAF9F5',
  border: '#3A3733',
  primary: '#E08A6B',
};

export function themeColorsFor(scheme: 'light' | 'dark' | null | undefined): ThemeColors {
  return scheme === 'dark' ? dark : light;
}
