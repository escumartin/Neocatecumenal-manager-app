import { useFocusEffect } from 'expo-router';
import { useCallback, useRef } from 'react';

export function useReloadOnFocus(reload: () => unknown) {
  const firstFocus = useRef(true);

  useFocusEffect(
    useCallback(() => {
      if (firstFocus.current) {
        firstFocus.current = false;
        return;
      }
      void reload();
    }, [reload]),
  );
}
