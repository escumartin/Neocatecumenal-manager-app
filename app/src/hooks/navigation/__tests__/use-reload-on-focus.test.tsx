import { renderHook } from '@testing-library/react-native';
import { useReloadOnFocus } from '../use-reload-on-focus';

const mockEffects: (() => void)[] = [];

jest.mock('expo-router', () => ({
  useFocusEffect: (effect: () => void) => {
    mockEffects.push(effect);
  },
}));

function focus() {
  mockEffects.at(-1)?.();
}

describe('reload on focus', () => {
  beforeEach(() => {
    mockEffects.length = 0;
  });

  it('does not reload on the first focus', async () => {
    const reload = jest.fn();
    await renderHook(() => useReloadOnFocus(reload));

    focus();

    expect(reload).not.toHaveBeenCalled();
  });

  it('reloads every time the screen regains focus', async () => {
    const reload = jest.fn();
    await renderHook(() => useReloadOnFocus(reload));

    focus();
    focus();
    focus();

    expect(reload).toHaveBeenCalledTimes(2);
  });
});
