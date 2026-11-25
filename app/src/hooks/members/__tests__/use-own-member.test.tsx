import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useOwnMember } from '../use-own-member';
import type { MyMember, MyMemberApi } from '@/lib/api/members-api';
import { ana } from '@/screens/members/support/test-support';

const mine: MyMember = { member: ana, currentConsents: [] };
const input = { mobile: { shared: true, value: '600000001' }, landline: { shared: false }, email: { shared: false } };

function fakeApi(overrides: Partial<MyMemberApi> = {}): MyMemberApi {
  return {
    get: jest.fn().mockResolvedValue({ ok: true, myMember: mine }),
    changeContact: jest.fn().mockResolvedValue({ ok: true, myMember: mine }),
    ...overrides,
  };
}

describe('useOwnMember', () => {
  it('loads the own member', async () => {
    const api = fakeApi();

    const { result } = await renderHook(() => useOwnMember(api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.myMember).toEqual(mine);
  });

  it('reports not linked when the user has no member', async () => {
    const api = fakeApi({ get: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'MEMBER_NOT_LINKED' }) });

    const { result } = await renderHook(() => useOwnMember(api));

    await waitFor(() => expect(result.current.status).toBe('not-linked'));
    expect(result.current.myMember).toBeNull();
  });

  it('reports an error for any other failure and reloads on demand', async () => {
    const api = fakeApi({
      get: jest
        .fn()
        .mockResolvedValueOnce({ ok: false, status: 500 })
        .mockResolvedValueOnce({ ok: true, myMember: mine }),
    });
    const { result } = await renderHook(() => useOwnMember(api));
    await waitFor(() => expect(result.current.status).toBe('error'));

    await act(async () => {
      await result.current.reload();
    });

    expect(result.current.status).toBe('ready');
  });

  it('reports an error when the request throws', async () => {
    const api = fakeApi({ get: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await renderHook(() => useOwnMember(api));

    await waitFor(() => expect(result.current.status).toBe('error'));
  });

  it('saves the contact and keeps the answer as the current data', async () => {
    const saved: MyMember = { member: { ...ana, mobile: '600000002' }, currentConsents: [] };
    const api = fakeApi({ changeContact: jest.fn().mockResolvedValue({ ok: true, myMember: saved }) });
    const { result } = await renderHook(() => useOwnMember(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome: Awaited<ReturnType<typeof result.current.save>> | undefined;
    await act(async () => {
      outcome = await result.current.save(input);
    });

    expect(api.changeContact).toHaveBeenCalledWith(input);
    expect(outcome).toEqual({ ok: true, myMember: saved });
    expect(result.current.myMember).toEqual(saved);
  });

  it('keeps the current data when the save is refused', async () => {
    const api = fakeApi({
      changeContact: jest
        .fn()
        .mockResolvedValue({ ok: false, status: 422, fieldErrors: [{ field: 'mobile', code: 'PHONE_INVALID' }] }),
    });
    const { result } = await renderHook(() => useOwnMember(api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.save(input);
    });

    expect(result.current.myMember).toEqual(mine);
  });
});
