import Constants from 'expo-constants';
import { useEffect, useState } from 'react';
import { AppState, Platform } from 'react-native';
import { serviceApi } from '@/lib/api';
import type { ServiceApi } from '@/lib/api/service-api';
import { isBelowMinimum } from '@/lib/compatibility/app-version';

type Options = {
  api?: ServiceApi;
  currentVersion?: string;
  platform?: string;
};

export function useAppCompatibility({
  api = serviceApi,
  currentVersion = Constants.expoConfig?.version ?? '',
  platform = Platform.OS,
}: Options = {}) {
  const [updateRequired, setUpdateRequired] = useState(false);

  useEffect(() => {
    if (platform === 'web') {
      return;
    }
    let active = true;

    async function check() {
      try {
        const result = await api.getCompatibility();
        if (active && result.ok) {
          setUpdateRequired(isBelowMinimum(currentVersion, result.compatibility.minimumAppVersion));
        }
      } catch {
        return;
      }
    }

    check();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        check();
      }
    });
    return () => {
      active = false;
      subscription.remove();
    };
  }, [api, currentVersion, platform]);

  return { updateRequired };
}
