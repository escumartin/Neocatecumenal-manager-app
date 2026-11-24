import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { sessionApi } from '@/lib/api';
import { sessionEvents } from '@/lib/api/session-events';
import type { Me, OwnActionResult, SessionApi } from '@/lib/api/session-api';
import type { PendingStepCode } from '@/lib/api/client';
import { stepForCode, type PendingStep } from '@/lib/auth/pending-steps';
import { sessionStore, type SessionStore } from '@/lib/auth/session-store';

export type { SessionApi };

export type SessionStatus = 'loading' | 'anonymous' | 'authenticated' | 'error';

export type LogInOutcome = { ok: true } | { ok: false; code: string; retryAfterSeconds?: number };

type SessionValue = {
  status: SessionStatus;
  me: Me | null;
  retry: () => void;
  logIn: (accessId: number, password: string) => Promise<LogInOutcome>;
  logOut: () => Promise<void>;
  acceptPrivacyNotice: () => Promise<OwnActionResult>;
  rejectPrivacyNotice: () => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<OwnActionResult>;
};

const SessionContext = createContext<SessionValue | null>(null);

type ProviderProps = {
  children: ReactNode;
  api?: SessionApi;
  store?: SessionStore;
};

export function SessionProvider({ children, api = sessionApi, store = sessionStore }: ProviderProps) {
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [me, setMe] = useState<Me | null>(null);
  const [attempt, setAttempt] = useState(0);

  const reset = useCallback(async () => {
    await store.clear();
    setMe(null);
    setStatus('anonymous');
  }, [store]);

  useEffect(() => {
    let active = true;
    api
      .getMe()
      .then((result) => {
        if (!active) {
          return;
        }
        if (result.ok) {
          setMe(result.me);
          setStatus('authenticated');
        } else {
          setStatus(result.status >= 500 ? 'error' : 'anonymous');
        }
      })
      .catch(() => {
        if (active) {
          setStatus('error');
        }
      });
    return () => {
      active = false;
    };
  }, [api, attempt]);

  const retry = useCallback(() => {
    setStatus('loading');
    setAttempt((current) => current + 1);
  }, []);

  const addStep = useCallback((code: PendingStepCode) => {
    const step = stepForCode(code);
    setMe((current) =>
      current && !current.pendingSteps.includes(step)
        ? { ...current, pendingSteps: [...current.pendingSteps, step] }
        : current,
    );
  }, []);

  const removeStep = useCallback((step: PendingStep) => {
    setMe((current) =>
      current ? { ...current, pendingSteps: current.pendingSteps.filter((pending) => pending !== step) } : current,
    );
  }, []);

  useEffect(
    () => sessionEvents.subscribe({ onUnauthorized: () => void reset(), onPendingStep: addStep }),
    [reset, addStep],
  );

  const logIn = useCallback<SessionValue['logIn']>(
    async (accessId, password) => {
      const result = await api.logIn(accessId, password);
      if (!result.ok) {
        return result;
      }
      if (result.token) {
        await store.save(result.token);
      }
      setMe(result.me);
      setStatus('authenticated');
      return { ok: true };
    },
    [api, store],
  );

  const logOut = useCallback(async () => {
    await api.logOut().catch(() => undefined);
    await reset();
  }, [api, reset]);

  const acceptPrivacyNotice = useCallback<SessionValue['acceptPrivacyNotice']>(async () => {
    const result = await api.acceptPrivacyNotice(me?.currentPrivacyNoticeVersion ?? '');
    if (result.ok) {
      removeStep('PRIVACY_NOTICE');
    }
    return result;
  }, [api, me, removeStep]);

  const rejectPrivacyNotice = useCallback(async () => {
    await api.rejectPrivacyNotice().catch(() => undefined);
    await reset();
  }, [api, reset]);

  const changePassword = useCallback<SessionValue['changePassword']>(
    async (currentPassword, newPassword) => {
      const result = await api.changePassword(currentPassword, newPassword);
      if (result.ok) {
        removeStep('PASSWORD_CHANGE');
      }
      return result;
    },
    [api, removeStep],
  );

  const value = useMemo(
    () => ({ status, me, retry, logIn, logOut, acceptPrivacyNotice, rejectPrivacyNotice, changePassword }),
    [status, me, retry, logIn, logOut, acceptPrivacyNotice, rejectPrivacyNotice, changePassword],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error('useSession must be used inside SessionProvider');
  }
  return value;
}
