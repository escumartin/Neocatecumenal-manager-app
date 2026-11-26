import '../global.css';

import { Inter_400Regular, Inter_500Medium, Inter_600SemiBold } from '@expo-google-fonts/inter';
import { SourceSerif4_400Regular, SourceSerif4_600SemiBold, useFonts } from '@expo-google-fonts/source-serif-4';
import { PortalHost } from '@rn-primitives/portal';
import Constants from 'expo-constants';
import { Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { Platform, useColorScheme } from 'react-native';
import { SessionProvider } from '@/hooks/session/use-session';
import { useAppCompatibility } from '@/hooks/compatibility/use-app-compatibility';
import { storeUrlFor } from '@/lib/compatibility/store-url';
import { navigationThemeFor } from '@/lib/theme/navigation-theme';
import { UpdateRequiredScreen } from '@/screens/update-required';

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const { updateRequired } = useAppCompatibility();
  const storeUrls = Constants.expoConfig?.extra?.storeUrls as Record<string, string> | undefined;
  const [fontsLoaded, fontError] = useFonts({
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    SourceSerif4_400Regular,
    SourceSerif4_600SemiBold,
  });

  useEffect(() => {
    if (fontsLoaded || fontError) {
      SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) {
    return null;
  }

  return (
    <>
      <StatusBar style="auto" />
      <ThemeProvider value={navigationThemeFor(colorScheme)}>
        <SessionProvider>
          {updateRequired ? (
            <UpdateRequiredScreen storeUrl={storeUrlFor(Platform.OS, storeUrls)} />
          ) : (
            <Stack screenOptions={{ headerShown: false }} />
          )}
        </SessionProvider>
      </ThemeProvider>
      <PortalHost />
    </>
  );
}
