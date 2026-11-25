import { Platform } from 'react-native';
import type { Stack } from 'expo-router';
import type { ComponentProps } from 'react';
import { CancelButton } from '@/components/navigation/cancel-button';

type ScreenOptions = Exclude<
  ComponentProps<typeof Stack.Screen>['options'],
  ((...args: never[]) => unknown) | undefined
>;

export const tabStackOptions: ScreenOptions = {
  headerLargeTitle: Platform.OS === 'ios',
  headerLargeTitleShadowVisible: false,
  headerBackButtonDisplayMode: 'minimal',
};

export const detailStackOptions: ScreenOptions = {
  headerLargeTitle: false,
};

export function formSheetOptions(title: string): ScreenOptions {
  return {
    title,
    presentation: 'formSheet',
    headerLargeTitle: false,
    sheetAllowedDetents: [1],
    sheetGrabberVisible: true,
    headerLeft: CancelButton,
  };
}
