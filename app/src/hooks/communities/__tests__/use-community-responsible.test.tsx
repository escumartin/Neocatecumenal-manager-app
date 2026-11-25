import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useCommunityResponsible } from '../use-community-responsible';
import { detailOf, fakeCommunitiesApi, five, twelve } from '@/screens/communities/support/test-support';
import { ana, fakeMembersApi } from '@/screens/members/support/test-support';

function deferred() {
  let resolve: (value: unknown) => void = () => undefined;
  const promise = new Promise((r) => (resolve = r));
  return { promise, resolve };
}

describe('useCommunityResponsible', () => {
  it('loads the community and its members', async () => {
    const api = fakeCommunitiesApi({
      get: jest.fn().mockResolvedValue({ ok: true, community: detailOf(twelve) }),
    });
    const membersApi = fakeMembersApi({
      list: jest.fn().mockResolvedValue({ ok: true, members: [{ ...ana, communityId: twelve.id }] }),
    });

    const { result } = await renderHook(() => useCommunityResponsible(twelve.id, api, membersApi));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.members).toHaveLength(1);
  });

  it('ignores a stale load that resolves after a newer reload', async () => {
    const stale = deferred();
    const get = jest
      .fn()
      .mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce({ ok: true, community: detailOf(five) });
    const api = fakeCommunitiesApi({ get });
    const membersApi = fakeMembersApi({ list: jest.fn().mockResolvedValue({ ok: true, members: [] }) });
    const { result } = await renderHook(() => useCommunityResponsible(twelve.id, api, membersApi));

    await act(() => result.current.reload());
    await act(async () => {
      stale.resolve({ ok: true, community: detailOf(twelve) });
    });

    expect(result.current.community).toEqual(detailOf(five));
  });
});
