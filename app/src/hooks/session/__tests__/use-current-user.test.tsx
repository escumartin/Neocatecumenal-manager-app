import { renderHook, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { SessionProvider, type SessionApi } from '../use-session';
import { useCurrentUser } from '../use-current-user';

const baseMe = {
  userId: '6f1c1f1e-0000-4000-8000-000000000001',
  accessId: 482913,
  name: 'Test User',
  communityId: '6f1c1f1e-0000-4000-8000-000000000002',
  communityNumber: 3,
  pendingSteps: [],
  currentPrivacyNoticeVersion: '1',
};

async function setupWith(me: (typeof baseMe & { role: string }) | null) {
  const api = {
    logIn: jest.fn(),
    logOut: jest.fn(),
    getMe: jest.fn().mockResolvedValue(me ? { ok: true, me } : { ok: false, status: 401 }),
  } as unknown as SessionApi;
  const store = { read: jest.fn().mockResolvedValue(null), save: jest.fn(), clear: jest.fn() };
  const wrapper = ({ children }: { children: ReactNode }) => (
    <SessionProvider api={api} store={store}>
      {children}
    </SessionProvider>
  );
  const rendered = await renderHook(() => useCurrentUser(), { wrapper });
  await waitFor(() => expect(rendered.result.current.status).not.toBe('loading'));
  return rendered.result;
}

describe('useCurrentUser', () => {
  it('has no user and no permissions when anonymous', async () => {
    const result = await setupWith(null);

    expect(result.current.user).toBeNull();
    expect(result.current.isModerator).toBe(false);
    expect(result.current.isAdministrator).toBe(false);
    expect(result.current.canManageUsers).toBe(false);
    expect(result.current.canChooseCommunity).toBe(false);
  });

  it('gives a registered user no management actions', async () => {
    const result = await setupWith({ ...baseMe, role: 'REGISTERED' });

    expect(result.current.user?.name).toBe('Test User');
    expect(result.current.canManageUsers).toBe(false);
    expect(result.current.canChooseCommunity).toBe(false);
  });

  it('lets a moderator manage users but not choose the community', async () => {
    const result = await setupWith({ ...baseMe, role: 'MODERATOR' });

    expect(result.current.isModerator).toBe(true);
    expect(result.current.canManageUsers).toBe(true);
    expect(result.current.canChooseCommunity).toBe(false);
  });

  it('lets an administrator manage users and choose the community', async () => {
    const result = await setupWith({ ...baseMe, role: 'ADMINISTRATOR' });

    expect(result.current.isModerator).toBe(true);
    expect(result.current.isAdministrator).toBe(true);
    expect(result.current.canManageUsers).toBe(true);
    expect(result.current.canChooseCommunity).toBe(true);
  });
});
