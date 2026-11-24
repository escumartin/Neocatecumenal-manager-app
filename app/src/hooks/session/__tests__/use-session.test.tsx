import { act, renderHook, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { SessionProvider, useSession, type SessionApi } from '../use-session';
import { sessionEvents } from '@/lib/api/session-events';

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

function createStore(initial: string | null = null) {
  let token = initial;
  return {
    read: jest.fn(async () => token),
    save: jest.fn(async (value: string) => {
      token = value;
    }),
    clear: jest.fn(async () => {
      token = null;
    }),
  };
}

function createApi(overrides: Partial<SessionApi> = {}): SessionApi {
  return {
    logIn: jest.fn().mockResolvedValue({ ok: true, token: 'token-1', me }),
    logOut: jest.fn().mockResolvedValue(undefined),
    getMe: jest.fn().mockResolvedValue({ ok: false, status: 401 }),
    changePassword: jest.fn().mockResolvedValue({ ok: true }),
    acceptPrivacyNotice: jest.fn().mockResolvedValue({ ok: true }),
    rejectPrivacyNotice: jest.fn().mockResolvedValue({ ok: true }),
    ...overrides,
  } as SessionApi;
}

async function setup(api: SessionApi, store = createStore()) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <SessionProvider api={api} store={store}>
      {children}
    </SessionProvider>
  );
  const rendered = await renderHook(() => useSession(), { wrapper });
  return { store, ...rendered };
}

describe('useSession', () => {
  it('starts anonymous when there is no session to restore', async () => {
    const { result } = await setup(createApi());

    await waitFor(() => expect(result.current.status).toBe('anonymous'));
  });

  it('reports an error instead of staying in loading when the session check cannot reach the server', async () => {
    const api = createApi({ getMe: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await setup(api);

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.me).toBeNull();
  });

  it('reports an error when the session check answers with a server failure', async () => {
    const api = createApi({ getMe: jest.fn().mockResolvedValue({ ok: false, status: 503 }) });

    const { result } = await setup(api);

    await waitFor(() => expect(result.current.status).toBe('error'));
  });

  it('checks the session again on retry and recovers', async () => {
    const getMe = jest
      .fn()
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce({ ok: true, me });
    const { result } = await setup(createApi({ getMe }));
    await waitFor(() => expect(result.current.status).toBe('error'));

    await act(async () => {
      result.current.retry();
    });

    await waitFor(() => expect(result.current.status).toBe('authenticated'));
    expect(getMe).toHaveBeenCalledTimes(2);
  });

  it('restores the session from the stored token', async () => {
    const api = createApi({ getMe: jest.fn().mockResolvedValue({ ok: true, me }) });

    const { result } = await setup(api, createStore('stored'));

    await waitFor(() => expect(result.current.status).toBe('authenticated'));
    expect(result.current.me).toEqual(me);
  });

  it('stores the token and authenticates after a bearer login', async () => {
    const { result, store } = await setup(createApi());
    await waitFor(() => expect(result.current.status).toBe('anonymous'));

    await act(async () => {
      await result.current.logIn(482913, 'secret-pass');
    });

    expect(store.save).toHaveBeenCalledWith('token-1');
    expect(result.current.status).toBe('authenticated');
    expect(result.current.me).toEqual(me);
  });

  it('authenticates without a stored token after a cookie login', async () => {
    const api = createApi({ logIn: jest.fn().mockResolvedValue({ ok: true, token: undefined, me }) });
    const { result, store } = await setup(api);
    await waitFor(() => expect(result.current.status).toBe('anonymous'));

    await act(async () => {
      await result.current.logIn(482913, 'secret-pass');
    });

    expect(store.save).not.toHaveBeenCalled();
    expect(result.current.status).toBe('authenticated');
  });

  it('exposes the generic failure code on invalid credentials', async () => {
    const api = createApi({
      logIn: jest.fn().mockResolvedValue({ ok: false, code: 'INVALID_CREDENTIALS' }),
    });
    const { result } = await setup(api);
    await waitFor(() => expect(result.current.status).toBe('anonymous'));

    let outcome;
    await act(async () => {
      outcome = await result.current.logIn(482913, 'wrong');
    });

    expect(outcome).toEqual({ ok: false, code: 'INVALID_CREDENTIALS' });
    expect(result.current.status).toBe('anonymous');
  });

  it('exposes the wait time when the login is blocked', async () => {
    const api = createApi({
      logIn: jest
        .fn()
        .mockResolvedValue({ ok: false, code: 'LOGIN_TEMPORARILY_BLOCKED', retryAfterSeconds: 120 }),
    });
    const { result } = await setup(api);
    await waitFor(() => expect(result.current.status).toBe('anonymous'));

    let outcome;
    await act(async () => {
      outcome = await result.current.logIn(482913, 'wrong');
    });

    expect(outcome).toEqual({ ok: false, code: 'LOGIN_TEMPORARILY_BLOCKED', retryAfterSeconds: 120 });
  });

  it('clears the stored token and returns to anonymous on logout', async () => {
    const api = createApi();
    const { result, store } = await setup(api);
    await waitFor(() => expect(result.current.status).toBe('anonymous'));
    await act(async () => {
      await result.current.logIn(482913, 'secret-pass');
    });

    await act(async () => {
      await result.current.logOut();
    });

    expect(api.logOut).toHaveBeenCalled();
    expect(store.clear).toHaveBeenCalled();
    expect(result.current.status).toBe('anonymous');
    expect(result.current.me).toBeNull();
  });

  it('drops the local session on logout even when the server call fails', async () => {
    const api = createApi({ logOut: jest.fn().mockRejectedValue(new Error('offline')) });
    const { result, store } = await setup(api);
    await waitFor(() => expect(result.current.status).toBe('anonymous'));
    await act(async () => {
      await result.current.logIn(482913, 'secret-pass');
    });

    await act(async () => {
      await result.current.logOut();
    });

    expect(store.clear).toHaveBeenCalled();
    expect(result.current.status).toBe('anonymous');
  });

  describe('pending steps', () => {
    const pendingMe = { ...me, pendingSteps: ['PRIVACY_NOTICE', 'PASSWORD_CHANGE'] as ('PRIVACY_NOTICE' | 'PASSWORD_CHANGE')[] };

    async function authenticated(api: SessionApi, store = createStore()) {
      const rendered = await setup(api, store);
      await waitFor(() => expect(rendered.result.current.status).toBe('authenticated'));
      return { ...rendered, store };
    }

    it('accepts the current privacy notice version and drops that step', async () => {
      const api = createApi({ getMe: jest.fn().mockResolvedValue({ ok: true, me: pendingMe }) });
      const { result } = await authenticated(api);

      let outcome;
      await act(async () => {
        outcome = await result.current.acceptPrivacyNotice();
      });

      expect(outcome).toEqual({ ok: true });
      expect(api.acceptPrivacyNotice).toHaveBeenCalledWith('1');
      expect(result.current.me?.pendingSteps).toEqual(['PASSWORD_CHANGE']);
    });

    it('keeps the step when the acceptance fails', async () => {
      const api = createApi({
        getMe: jest.fn().mockResolvedValue({ ok: true, me: pendingMe }),
        acceptPrivacyNotice: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'PRIVACY_NOTICE_OUTDATED' }),
      });
      const { result } = await authenticated(api);

      await act(async () => {
        await result.current.acceptPrivacyNotice();
      });

      expect(result.current.me?.pendingSteps).toEqual(['PRIVACY_NOTICE', 'PASSWORD_CHANGE']);
    });

    it('ends the local session when the privacy notice is rejected', async () => {
      const api = createApi({ getMe: jest.fn().mockResolvedValue({ ok: true, me: pendingMe }) });
      const { result, store } = await authenticated(api);

      await act(async () => {
        await result.current.rejectPrivacyNotice();
      });

      expect(api.rejectPrivacyNotice).toHaveBeenCalled();
      expect(store.clear).toHaveBeenCalled();
      expect(result.current.status).toBe('anonymous');
    });

    it('changes the password and drops that step', async () => {
      const api = createApi({
        getMe: jest.fn().mockResolvedValue({ ok: true, me: { ...pendingMe, pendingSteps: ['PASSWORD_CHANGE'] } }),
      });
      const { result } = await authenticated(api);

      let outcome;
      await act(async () => {
        outcome = await result.current.changePassword('old-secret', 'new-secret-1');
      });

      expect(outcome).toEqual({ ok: true });
      expect(api.changePassword).toHaveBeenCalledWith('old-secret', 'new-secret-1');
      expect(result.current.me?.pendingSteps).toEqual([]);
    });

    it('keeps the step and returns the field errors when the password is rejected', async () => {
      const failure = { ok: false, status: 422, code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'newPassword', code: 'PASSWORD_TOO_SHORT' }] };
      const api = createApi({
        getMe: jest.fn().mockResolvedValue({ ok: true, me: { ...pendingMe, pendingSteps: ['PASSWORD_CHANGE'] } }),
        changePassword: jest.fn().mockResolvedValue(failure),
      });
      const { result } = await authenticated(api);

      let outcome;
      await act(async () => {
        outcome = await result.current.changePassword('old-secret', 'short');
      });

      expect(outcome).toEqual(failure);
      expect(result.current.me?.pendingSteps).toEqual(['PASSWORD_CHANGE']);
    });

    it.each([
      ['PRIVACY_NOTICE_PENDING', 'PRIVACY_NOTICE'],
      ['PRIVACY_NOTICE_OUTDATED', 'PRIVACY_NOTICE'],
      ['PASSWORD_CHANGE_REQUIRED', 'PASSWORD_CHANGE'],
    ] as const)('adds the pending step reported by the server as %s', async (code, step) => {
      const api = createApi({ getMe: jest.fn().mockResolvedValue({ ok: true, me }) });
      const { result } = await authenticated(api);

      await act(async () => {
        sessionEvents.pendingStep(code);
      });

      expect(result.current.me?.pendingSteps).toEqual([step]);
    });

    it('does not duplicate a step that is already pending', async () => {
      const api = createApi({ getMe: jest.fn().mockResolvedValue({ ok: true, me: pendingMe }) });
      const { result } = await authenticated(api);

      await act(async () => {
        sessionEvents.pendingStep('PRIVACY_NOTICE_PENDING');
      });

      expect(result.current.me?.pendingSteps).toEqual(['PRIVACY_NOTICE', 'PASSWORD_CHANGE']);
    });
  });
});
