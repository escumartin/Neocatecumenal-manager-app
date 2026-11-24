import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useUsers } from '../use-users';
import type { UsersApi } from '@/lib/api/users-api';

const communityId = '6f1c1f1e-0000-4000-8000-000000000011';
const first = { id: '6f1c1f1e-0000-4000-8000-000000000021', accessId: 482913, name: 'Ana', role: 'REGISTERED' as const, communityId, passwordChangePending: false };
const second = { ...first, id: '6f1c1f1e-0000-4000-8000-000000000022', accessId: 482914, name: 'Luis' };

function fakeApi(overrides: Partial<UsersApi> = {}): UsersApi {
  return {
    list: jest.fn().mockResolvedValue({ ok: true, users: [first, second] }),
    get: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    remove: jest.fn(),
    resetPassword: jest.fn(),
    ...overrides,
  };
}

describe('useUsers', () => {
  it('loads the users of the community', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useUsers(communityId, api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.users).toEqual([first, second]);
    expect(api.list).toHaveBeenCalledWith(communityId);
  });

  it('reports an error when the list cannot be loaded', async () => {
    const api = fakeApi({ list: jest.fn().mockResolvedValue({ ok: false, status: 500 }) });

    const { result } = await renderHook(() => useUsers(communityId, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.users).toEqual([]);
  });

  it('does not call the server when disabled', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useUsers(communityId, api, false));

    expect(result.current.status).toBe('idle');
    expect(api.list).not.toHaveBeenCalled();
  });

  it('reloads the list when the community changes', async () => {
    const api = fakeApi();
    const { rerender } = await renderHook(({ id }: { id: string }) => useUsers(id, api), {
      initialProps: { id: communityId },
    });

    await rerender({ id: '6f1c1f1e-0000-4000-8000-000000000012' });

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith('6f1c1f1e-0000-4000-8000-000000000012'));
  });

  it('drops a user from the list after deleting it', async () => {
    const api = fakeApi({ remove: jest.fn().mockResolvedValue({ ok: true }) });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.remove(first.id);
    });

    expect(outcome).toEqual({ ok: true });
    expect(result.current.users).toEqual([second]);
  });

  it('keeps the user in the list when the deletion is refused', async () => {
    const refusal = { ok: false, status: 409, code: 'LAST_MODERATOR' };
    const api = fakeApi({ remove: jest.fn().mockResolvedValue(refusal) });
    const { result } = await renderHook(() => useUsers(communityId, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.remove(first.id);
    });

    expect(outcome).toEqual(refusal);
    expect(result.current.users).toEqual([first, second]);
  });

  it('delegates creation to the api', async () => {
    const created = { ...first, initialPassword: 'abcd234567' };
    const api = fakeApi({ create: jest.fn().mockResolvedValue({ ok: true, user: created }) });
    const { result } = await renderHook(() => useUsers(communityId, api));

    let outcome;
    await act(async () => {
      outcome = await result.current.create({ name: 'Ana', role: 'REGISTERED', communityId });
    });

    expect(outcome).toEqual({ ok: true, user: created });
    expect(api.create).toHaveBeenCalledWith({ name: 'Ana', role: 'REGISTERED', communityId });
  });
});
