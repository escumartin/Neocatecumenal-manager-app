import { renderHook, waitFor } from '@testing-library/react-native';
import { useUser } from '../use-user';
import type { UsersApi } from '@/lib/api/users-api';

const user = {
  id: '6f1c1f1e-0000-4000-8000-000000000021',
  accessId: 482913,
  name: 'Ana',
  role: 'REGISTERED' as const,
  communityId: '6f1c1f1e-0000-4000-8000-000000000011',
  passwordChangePending: false,
};

function fakeApi(get: UsersApi['get']): UsersApi {
  return { list: jest.fn(), get, create: jest.fn(), update: jest.fn(), remove: jest.fn(), resetPassword: jest.fn() };
}

describe('useUser', () => {
  it('loads one user', async () => {
    const api = fakeApi(jest.fn().mockResolvedValue({ ok: true, user }));
    const { result } = await renderHook(() => useUser(user.id, api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.user).toEqual(user);
  });

  it('reports a missing user', async () => {
    const api = fakeApi(jest.fn().mockResolvedValue({ ok: false, status: 404 }));
    const { result } = await renderHook(() => useUser(user.id, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.user).toBeNull();
  });

  it('saves changes against the loaded user', async () => {
    const update = jest.fn().mockResolvedValue({ ok: true, user });
    const api = { ...fakeApi(jest.fn().mockResolvedValue({ ok: true, user })), update };
    const { result } = await renderHook(() => useUser(user.id, api));
    const input = { name: 'Ana', role: 'REGISTERED' as const, communityId: user.communityId };

    await result.current.save(input);

    expect(update).toHaveBeenCalledWith(user.id, input);
  });

  it('refuses to save when there is no user to update', async () => {
    const update = jest.fn();
    const api = { ...fakeApi(jest.fn()), update };
    const { result } = await renderHook(() => useUser(undefined, api));
    const input = { name: 'Ana', role: 'REGISTERED' as const, communityId: user.communityId };

    await expect(result.current.save(input)).resolves.toEqual({ ok: false, status: 404 });
    expect(update).not.toHaveBeenCalled();
  });
});
