import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useMember } from '../use-member';
import { useMemberSave } from '../use-member-save';
import { useMemberContactSave } from '../use-member-contact-save';
import { ana, fakeMembersApi } from '@/screens/members/support/test-support';

describe('useMember', () => {
  it('loads the member', async () => {
    const api = fakeMembersApi();

    const { result } = await renderHook(() => useMember(ana.id, api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.member).toEqual(ana);
  });

  it('reports an error when the member cannot be loaded and reloads on demand', async () => {
    const api = fakeMembersApi({
      get: jest
        .fn()
        .mockResolvedValueOnce({ ok: false, status: 404 })
        .mockResolvedValueOnce({ ok: true, member: ana }),
    });
    const { result } = await renderHook(() => useMember(ana.id, api));
    await waitFor(() => expect(result.current.status).toBe('error'));

    await act(async () => {
      await result.current.reload();
    });

    expect(result.current.status).toBe('ready');
  });

  it('reports an error when the request fails', async () => {
    const api = fakeMembersApi({ get: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await renderHook(() => useMember(ana.id, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.member).toBeNull();
  });
});

describe('useMemberSave', () => {
  it('creates a member when there is no id', async () => {
    const api = fakeMembersApi();
    const { result } = await renderHook(() => useMemberSave(undefined, api));

    await act(async () => {
      await result.current.save({ communityId: ana.communityId, givenName: 'Ana' });
    });

    expect(api.create).toHaveBeenCalledWith({ communityId: ana.communityId, givenName: 'Ana' });
    expect(api.rename).not.toHaveBeenCalled();
  });

  it('renames the member sending only the names', async () => {
    const api = fakeMembersApi();
    const { result } = await renderHook(() => useMemberSave(ana.id, api));

    await act(async () => {
      await result.current.save({ communityId: ana.communityId, givenName: 'Ana', surnames: 'Pérez' });
    });

    expect(api.rename).toHaveBeenCalledWith(ana.id, { givenName: 'Ana', surnames: 'Pérez' });
    expect(api.create).not.toHaveBeenCalled();
  });
});

describe('useMemberContactSave', () => {
  it('changes the contact of the member', async () => {
    const api = fakeMembersApi();
    const { result } = await renderHook(() => useMemberContactSave(ana.id, api));
    const input = { mobile: { shared: false }, landline: { shared: false }, email: { shared: false } };

    await act(async () => {
      await result.current.save(input);
    });

    expect(api.changeContact).toHaveBeenCalledWith(ana.id, input);
  });
});
