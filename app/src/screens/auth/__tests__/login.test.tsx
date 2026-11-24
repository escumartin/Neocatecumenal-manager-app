import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { Login } from '../login';
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

async function renderLogin(logIn: SessionApi['logIn']) {
  const api = {
    logIn,
    logOut: jest.fn(),
    getMe: jest.fn().mockResolvedValue({ ok: false, status: 401 }),
  } as unknown as SessionApi;
  const store = { read: jest.fn().mockResolvedValue(null), save: jest.fn(), clear: jest.fn() };
  await render(
    <SessionProvider api={api} store={store}>
      <Login />
    </SessionProvider>,
  );
  return api;
}

async function fillAndSubmit(accessId: string, password: string) {
  await fireEvent.changeText(await screen.findByLabelText(identity.login.accessIdLabel), accessId);
  await fireEvent.changeText(screen.getByLabelText(identity.login.passwordLabel), password);
  await fireEvent.press(screen.getByRole('button', { name: identity.login.submit }));
}

describe('login screen', () => {
  it('labels both fields and the submit button', async () => {
    await renderLogin(jest.fn());

    expect(await screen.findByLabelText(identity.login.accessIdLabel)).toBeOnTheScreen();
    expect(screen.getByLabelText(identity.login.passwordLabel)).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: identity.login.submit })).toBeOnTheScreen();
  });

  it('hides the password by default', async () => {
    await renderLogin(jest.fn());

    expect((await screen.findByLabelText(identity.login.passwordLabel)).props.secureTextEntry).toBe(true);
  });

  it('shows and hides the password with a labelled toggle', async () => {
    await renderLogin(jest.fn());
    await screen.findByLabelText(identity.login.passwordLabel);

    await fireEvent.press(screen.getByRole('button', { name: identity.login.showPassword }));
    expect(screen.getByLabelText(identity.login.passwordLabel).props.secureTextEntry).toBe(false);

    await fireEvent.press(screen.getByRole('button', { name: identity.login.hidePassword }));
    expect(screen.getByLabelText(identity.login.passwordLabel).props.secureTextEntry).toBe(true);
  });

  it('submits the access id as a number with the password', async () => {
    const logIn = jest.fn().mockResolvedValue({ ok: true, token: 't', me });
    await renderLogin(logIn);

    await fillAndSubmit('482913', 'secret-pass');

    await waitFor(() => expect(logIn).toHaveBeenCalledWith(482913, 'secret-pass'));
  });

  it('shows one generic message on invalid credentials', async () => {
    await renderLogin(jest.fn().mockResolvedValue({ ok: false, code: 'INVALID_CREDENTIALS' }));

    await fillAndSubmit('482913', 'wrong');

    expect(await screen.findByRole('alert')).toHaveTextContent(identity.errors.INVALID_CREDENTIALS, { exact: false });
  });

  it('shows the wait time in minutes when the login is blocked', async () => {
    await renderLogin(
      jest.fn().mockResolvedValue({ ok: false, code: 'LOGIN_TEMPORARILY_BLOCKED', retryAfterSeconds: 125 }),
    );

    await fillAndSubmit('482913', 'wrong');

    expect(await screen.findByRole('alert')).toHaveTextContent(identity.blocked(125), { exact: false });
  });

  it('asks for both fields without calling the server when they are empty', async () => {
    const logIn = jest.fn();
    await renderLogin(logIn);
    await screen.findByLabelText(identity.login.accessIdLabel);

    await fireEvent.press(screen.getByRole('button', { name: identity.login.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(identity.login.required, { exact: false });
    expect(logIn).not.toHaveBeenCalled();
  });

  it('shows a generic failure message when the server is unreachable', async () => {
    await renderLogin(jest.fn().mockResolvedValue({ ok: false, code: 'UNKNOWN' }));

    await fillAndSubmit('482913', 'secret-pass');

    expect(await screen.findByRole('alert')).toHaveTextContent(identity.errors.UNKNOWN, { exact: false });
  });

  it('leaves the login for the home once the user is authenticated', async () => {
    const logIn = jest.fn().mockResolvedValue({ ok: true, token: 't', me });
    await renderLogin(logIn);

    await fillAndSubmit('482913', 'secret-pass');

    expect(await screen.findByText('redirect:/')).toBeOnTheScreen();
  });

  it('re-enables the submit button and explains the failure when the call throws', async () => {
    await renderLogin(jest.fn().mockRejectedValue(new Error('network down')));

    await fillAndSubmit('482913', 'secret-pass');

    expect(await screen.findByRole('alert')).toHaveTextContent(identity.errors.UNKNOWN, { exact: false });
    expect(screen.getByRole('button', { name: identity.login.submit })).not.toBeDisabled();
  });

  it('limits the password to 64 characters', async () => {
    await renderLogin(jest.fn());

    expect((await screen.findByLabelText(identity.login.passwordLabel)).props.maxLength).toBe(64);
  });

  it('keeps taps on its buttons working while the keyboard is open', async () => {
    await renderLogin(jest.fn());
    await screen.findByLabelText(identity.login.passwordLabel);

    expect(screen.root?.props.keyboardShouldPersistTaps).toBe('handled');
  });
});
