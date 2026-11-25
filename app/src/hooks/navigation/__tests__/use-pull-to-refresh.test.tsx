import { act, renderHook } from '@testing-library/react-native';
import { usePullToRefresh } from '../use-pull-to-refresh';

describe('usePullToRefresh', () => {
  it('is not refreshing at first', async () => {
    const { result } = await renderHook(() => usePullToRefresh(jest.fn().mockResolvedValue(undefined)));

    expect(result.current.refreshing).toBe(false);
  });

  it('is refreshing while the reload runs and stops afterwards', async () => {
    let finish: () => void = () => undefined;
    const reload = jest.fn().mockReturnValue(new Promise<void>((resolve) => (finish = resolve)));
    const { result } = await renderHook(() => usePullToRefresh(reload));

    await act(async () => {
      void result.current.onRefresh();
    });
    expect(result.current.refreshing).toBe(true);
    expect(reload).toHaveBeenCalledTimes(1);

    await act(async () => {
      finish();
    });
    expect(result.current.refreshing).toBe(false);
  });

  it('stops refreshing when the reload fails', async () => {
    const reload = jest.fn().mockRejectedValue(new Error('offline'));
    const { result } = await renderHook(() => usePullToRefresh(reload));

    await act(async () => {
      await result.current.onRefresh();
    });

    expect(result.current.refreshing).toBe(false);
  });
});
