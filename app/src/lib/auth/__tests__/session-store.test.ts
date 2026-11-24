import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { createSessionStore } from '../session-store';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

const secureStore = jest.mocked(SecureStore);

describe('session store', () => {
  const originalOs = Platform.OS;

  afterEach(() => {
    Platform.OS = originalOs;
    jest.clearAllMocks();
  });

  describe('on native', () => {
    beforeEach(() => {
      Platform.OS = 'ios';
    });

    it('saves the token in secure storage', async () => {
      await createSessionStore().save('token-1');

      expect(secureStore.setItemAsync).toHaveBeenCalledWith('session-token', 'token-1');
    });

    it('reads the token from secure storage', async () => {
      secureStore.getItemAsync.mockResolvedValue('token-2');

      await expect(createSessionStore().read()).resolves.toBe('token-2');
    });

    it('returns null when nothing is stored', async () => {
      secureStore.getItemAsync.mockResolvedValue(null);

      await expect(createSessionStore().read()).resolves.toBeNull();
    });

    it('removes the token from secure storage', async () => {
      await createSessionStore().clear();

      expect(secureStore.deleteItemAsync).toHaveBeenCalledWith('session-token');
    });
  });

  describe('on web', () => {
    beforeEach(() => {
      Platform.OS = 'web';
    });

    it('never persists the token', async () => {
      const store = createSessionStore();

      await store.save('token-3');

      expect(secureStore.setItemAsync).not.toHaveBeenCalled();
      await expect(store.read()).resolves.toBeNull();
    });

    it('does not touch secure storage when clearing', async () => {
      await createSessionStore().clear();

      expect(secureStore.deleteItemAsync).not.toHaveBeenCalled();
    });
  });
});
