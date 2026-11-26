import { render, screen } from '@testing-library/react-native';
import Constants from 'expo-constants';
import { Platform, Text } from 'react-native';
import RootLayout from '@/app/_layout';
import { useAppCompatibility } from '@/hooks/compatibility/use-app-compatibility';

jest.mock('@/global.css', () => ({}));
jest.mock('@expo-google-fonts/inter', () => ({
  Inter_400Regular: 1,
  Inter_500Medium: 2,
  Inter_600SemiBold: 3,
}));
jest.mock('@expo-google-fonts/source-serif-4', () => ({
  SourceSerif4_400Regular: 4,
  SourceSerif4_600SemiBold: 5,
  useFonts: () => [true, null],
}));
jest.mock('expo-constants', () => ({ __esModule: true, default: { expoConfig: {} } }));
jest.mock('expo-splash-screen', () => ({
  preventAutoHideAsync: jest.fn(),
  hideAsync: jest.fn(),
}));
jest.mock('expo-router', () => {
  const { Text: MockText } = jest.requireActual('react-native');
  return {
    Stack: () => <MockText>navigation stack</MockText>,
    ThemeProvider: ({ children }: { children: React.ReactNode }) => children,
  };
});
jest.mock('@/lib/theme/navigation-theme', () => ({ navigationThemeFor: () => ({}) }));
jest.mock('@/hooks/session/use-session', () => ({
  SessionProvider: ({ children }: { children: React.ReactNode }) => children,
}));
jest.mock('@/hooks/compatibility/use-app-compatibility');
jest.mock('@/screens/update-required', () => {
  const { Text: MockText } = jest.requireActual('react-native');
  return {
    UpdateRequiredScreen: ({ storeUrl }: { storeUrl?: string }) => (
      <MockText>{`update required ${storeUrl ?? 'no-url'}`}</MockText>
    ),
  };
});

const mockedCompatibility = jest.mocked(useAppCompatibility);
const storeUrls = { ios: 'https://apps.example.test/ios', android: 'https://play.example.test/android' };

function setExtra(extra: Record<string, unknown> | undefined) {
  Constants.expoConfig = { extra } as typeof Constants.expoConfig;
}

describe('RootLayout', () => {
  const originalOs = Platform.OS;

  afterEach(() => {
    Object.defineProperty(Platform, 'OS', { value: originalOs, configurable: true });
  });

  it('renders the navigation stack when no update is required', async () => {
    mockedCompatibility.mockReturnValue({ updateRequired: false });
    setExtra({ storeUrls });

    await render(<RootLayout />);

    expect(screen.getByText('navigation stack')).toBeOnTheScreen();
  });

  it('blocks the app with the iOS store address on ios', async () => {
    mockedCompatibility.mockReturnValue({ updateRequired: true });
    Object.defineProperty(Platform, 'OS', { value: 'ios', configurable: true });
    setExtra({ storeUrls });

    await render(<RootLayout />);

    expect(screen.getByText(`update required ${storeUrls.ios}`)).toBeOnTheScreen();
    expect(screen.queryByText('navigation stack')).not.toBeOnTheScreen();
  });

  it('blocks the app with the Android store address on android', async () => {
    mockedCompatibility.mockReturnValue({ updateRequired: true });
    Object.defineProperty(Platform, 'OS', { value: 'android', configurable: true });
    setExtra({ storeUrls });

    await render(<RootLayout />);

    expect(screen.getByText(`update required ${storeUrls.android}`)).toBeOnTheScreen();
  });

  it('blocks the app without an address when none is configured', async () => {
    mockedCompatibility.mockReturnValue({ updateRequired: true });
    Object.defineProperty(Platform, 'OS', { value: 'ios', configurable: true });
    setExtra({ storeUrls: { ios: '', android: '' } });

    await render(<RootLayout />);

    expect(screen.getByText('update required no-url')).toBeOnTheScreen();
  });
});
