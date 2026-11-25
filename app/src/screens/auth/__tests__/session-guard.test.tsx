import { fireEvent, render, screen } from '@testing-library/react-native';
import { Text } from 'react-native';
import { SessionGuard } from '../session-guard';
import { SessionProvider, type SessionApi } from '@/hooks/session/use-session';
import { identity } from '@/i18n/es/identity';

jest.mock('expo-router', () => {
  const { Text: MockText } = jest.requireActual('react-native');
  return {
    Redirect: ({ href }: { href: string }) => <MockText>{`redirect:${href}`}</MockText>,
  };
});

const me = {
  userId: '6f1c1f1e-0000-4000-8000-000000000001',
  accessId: 482913,
  name: 'Test User',
  role: 'REGISTERED' as const,
  communityId: '6f1c1f1e-0000-4000-8000-000000000002',
  communityNumber: 3,
  pendingSteps: [],
  currentPrivacyNoticeVersion: '1',
};

async function renderGuard(getMe: SessionApi['getMe']) {
  const api = { logIn: jest.fn(), logOut: jest.fn(), getMe } as unknown as SessionApi;
  const store = { read: jest.fn().mockResolvedValue(null), save: jest.fn(), clear: jest.fn() };
  await render(
    <SessionProvider api={api} store={store}>
      <SessionGuard>
        <Text>protected content</Text>
      </SessionGuard>
    </SessionProvider>,
  );
}

describe('session guard', () => {
  it('sends a visitor without session to the login', async () => {
    await renderGuard(jest.fn().mockResolvedValue({ ok: false, status: 401 }));

    expect(await screen.findByText('redirect:/login')).toBeOnTheScreen();
    expect(screen.queryByText('protected content')).toBeNull();
  });

  it('shows the protected content without a logout button for an authenticated user', async () => {
    await renderGuard(jest.fn().mockResolvedValue({ ok: true, me }));

    expect(await screen.findByText('protected content')).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: identity.logout })).toBeNull();
  });

  it('sends a user with the privacy notice pending to it before anything else', async () => {
    const pending = {
      ...me,
      pendingSteps: ['PASSWORD_CHANGE', 'PRIVACY_NOTICE'] as ('PRIVACY_NOTICE' | 'PASSWORD_CHANGE')[],
    };
    await renderGuard(jest.fn().mockResolvedValue({ ok: true, me: pending }));

    expect(await screen.findByText('redirect:/privacy-notice')).toBeOnTheScreen();
    expect(screen.queryByText('protected content')).toBeNull();
  });

  it('sends a user with only the password change pending to it', async () => {
    const pending = { ...me, pendingSteps: ['PASSWORD_CHANGE'] as 'PASSWORD_CHANGE'[] };
    await renderGuard(jest.fn().mockResolvedValue({ ok: true, me: pending }));

    expect(await screen.findByText('redirect:/change-password')).toBeOnTheScreen();
    expect(screen.queryByText('protected content')).toBeNull();
  });

  it('offers a retry instead of the login when the session cannot be checked', async () => {
    const getMe = jest.fn().mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce({ ok: true, me });
    await renderGuard(getMe);

    expect(await screen.findByRole('alert')).toHaveTextContent(identity.sessionUnavailable.message, { exact: false });
    expect(screen.queryByText('redirect:/login')).not.toBeOnTheScreen();

    await fireEvent.press(screen.getByRole('button', { name: identity.sessionUnavailable.retry }));

    expect(await screen.findByText('protected content')).toBeOnTheScreen();
  });
});
