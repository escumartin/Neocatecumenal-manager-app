import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const TOKEN_KEY = 'session-token';

export type SessionStore = {
  read: () => Promise<string | null>;
  save: (token: string) => Promise<void>;
  clear: () => Promise<void>;
};

export function createSessionStore(): SessionStore {
  const isWeb = () => Platform.OS === 'web';
  return {
    read: async () => (isWeb() ? null : SecureStore.getItemAsync(TOKEN_KEY)),
    save: async (token) => {
      if (!isWeb()) {
        await SecureStore.setItemAsync(TOKEN_KEY, token);
      }
    },
    clear: async () => {
      if (!isWeb()) {
        await SecureStore.deleteItemAsync(TOKEN_KEY);
      }
    },
  };
}

export const sessionStore = createSessionStore();
