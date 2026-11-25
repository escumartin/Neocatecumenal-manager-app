import { act, renderHook, waitFor } from '@testing-library/react-native';
import { useCommunity } from '../use-community';
import { useCommunitySave } from '../use-community-save';
import { detailOf, fakeCommunitiesApi, twelve } from '@/screens/communities/support/test-support';

describe('useCommunity', () => {
  it('loads the community file', async () => {
    const api = fakeCommunitiesApi();

    const { result } = await renderHook(() => useCommunity(twelve.id, api));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.community).toEqual(detailOf(twelve));
    expect(api.get).toHaveBeenCalledWith(twelve.id);
  });

  it('reports an error when the file cannot be loaded', async () => {
    const api = fakeCommunitiesApi({ get: jest.fn().mockResolvedValue({ ok: false, status: 404 }) });

    const { result } = await renderHook(() => useCommunity(twelve.id, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.community).toBeNull();
  });

  it('reports an error when the request fails', async () => {
    const api = fakeCommunitiesApi({ get: jest.fn().mockRejectedValue(new Error('network down')) });

    const { result } = await renderHook(() => useCommunity(twelve.id, api));

    await waitFor(() => expect(result.current.status).toBe('error'));
  });

  it('reloads the file after a deactivation', async () => {
    const api = fakeCommunitiesApi();
    const { result } = await renderHook(() => useCommunity(twelve.id, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.deactivate();
    });

    expect(outcome).toEqual({ ok: true });
    expect(api.deactivate).toHaveBeenCalledWith(twelve.id, true);
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('does not reload the file when the deactivation is refused', async () => {
    const api = fakeCommunitiesApi({
      deactivate: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'COMMUNITY_HAS_PRIVILEGED_USERS' }),
    });
    const { result } = await renderHook(() => useCommunity(twelve.id, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.deactivate();
    });

    expect(outcome).toMatchObject({ ok: false, code: 'COMMUNITY_HAS_PRIVILEGED_USERS' });
    expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('reloads the file after a reactivation', async () => {
    const api = fakeCommunitiesApi();
    const { result } = await renderHook(() => useCommunity(twelve.id, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    await act(async () => {
      await result.current.reactivate();
    });

    expect(api.reactivate).toHaveBeenCalledWith(twelve.id);
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('deletes the community', async () => {
    const api = fakeCommunitiesApi();
    const { result } = await renderHook(() => useCommunity(twelve.id, api));
    await waitFor(() => expect(result.current.status).toBe('ready'));

    let outcome;
    await act(async () => {
      outcome = await result.current.remove();
    });

    expect(outcome).toEqual({ ok: true });
    expect(api.remove).toHaveBeenCalledWith(twelve.id);
  });
});

describe('useCommunitySave', () => {
  it('creates a community when there is no id', async () => {
    const api = fakeCommunitiesApi();
    const { result } = await renderHook(() => useCommunitySave(undefined, api));

    await act(async () => {
      await result.current.save({ number: 7 });
    });

    expect(api.create).toHaveBeenCalledWith({ number: 7 });
    expect(api.update).not.toHaveBeenCalled();
  });

  it('updates the community when there is an id', async () => {
    const api = fakeCommunitiesApi();
    const { result } = await renderHook(() => useCommunitySave(twelve.id, api));

    await act(async () => {
      await result.current.save({ number: 7, name: 'Renamed' });
    });

    expect(api.update).toHaveBeenCalledWith(twelve.id, { number: 7, name: 'Renamed' });
    expect(api.create).not.toHaveBeenCalled();
  });
});
