import { useCallback, useState } from 'react';

export function usePullToRefresh(reload: () => Promise<unknown>) {
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await reload();
    } catch {
      return;
    } finally {
      setRefreshing(false);
    }
  }, [reload]);

  return { refreshing, onRefresh };
}
