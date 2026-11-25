import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Alert, Platform } from 'react-native';
import { CommunitiesList } from '../list';
import { CommunityEdit } from '../edit';
import { closed, detailOf, fakeCommunitiesApi, five, renderAs, twelve } from '../support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { communitiesText } from '@/i18n/es/communities';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));
jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

function useWebDialog() {
  jest.replaceProperty(Platform, 'OS', 'web');
}

function nativeAlert() {
  return jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
}

function list(api = fakeCommunitiesApi()) {
  const onCreate = jest.fn();
  const onOpen = jest.fn();
  return { api, onCreate, onOpen, element: <CommunitiesList api={api} onCreate={onCreate} onOpen={onOpen} /> };
}

function edit(communityId?: string, api = fakeCommunitiesApi()) {
  const onSaved = jest.fn();
  const onDeleted = jest.fn();
  return {
    api,
    onSaved,
    onDeleted,
    element: <CommunityEdit communityId={communityId} api={api} onSaved={onSaved} onDeleted={onDeleted} />,
  };
}

describe('communities list', () => {
  it('shows the number, the optional name, the state and the missing responsible of each community', async () => {
    const { element } = list();
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(communitiesText.option(12))).toBeOnTheScreen();
    expect(screen.getByText(communitiesText.option(5))).toBeOnTheScreen();
    expect(screen.getByText(communitiesText.option(3))).toBeOnTheScreen();
    expect(screen.getByText('San Pedro')).toBeOnTheScreen();
    expect(screen.getByText(`Santa Ana · ${communitiesText.list.deactivated}`)).toBeOnTheScreen();
    expect(screen.getAllByText(communitiesText.list.noResponsible)).toHaveLength(2);
  });

  it('does not flag the community that has a responsible', async () => {
    const { element } = list(fakeCommunitiesApi({ list: jest.fn().mockResolvedValue({ ok: true, communities: [five] }) }));
    await renderAs('ADMINISTRATOR', element);

    await screen.findByText(communitiesText.option(5));

    expect(screen.queryByText(communitiesText.list.noResponsible)).not.toBeOnTheScreen();
  });

  it('asks the server for the deactivated communities too', async () => {
    const { element, api } = list();
    await renderAs('ADMINISTRATOR', element);

    await screen.findByText(communitiesText.option(12));

    expect(api.list).toHaveBeenCalledWith({ includeDeactivated: true });
  });

  it('shows the loading state first', async () => {
    const { element } = list(fakeCommunitiesApi({ list: jest.fn().mockReturnValue(new Promise(() => undefined)) }));
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(communitiesText.list.loading)).toBeOnTheScreen();
  });

  it('shows an empty state when there are no communities', async () => {
    const { element } = list(fakeCommunitiesApi({ list: jest.fn().mockResolvedValue({ ok: true, communities: [] }) }));
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(communitiesText.list.empty)).toBeOnTheScreen();
  });

  it('shows an error with a retry that loads again', async () => {
    const listCommunities = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, communities: [twelve] });
    const { element } = list(fakeCommunitiesApi({ list: listCommunities }));
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.list.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.retry }));

    expect(await screen.findByText(communitiesText.option(12))).toBeOnTheScreen();
  });

  it('opens the create form and the community file', async () => {
    const { element, onCreate, onOpen } = list();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.list.create }));
    await fireEvent.press(screen.getByRole('button', { name: new RegExp(communitiesText.option(12)) }));

    expect(onCreate).toHaveBeenCalledTimes(1);
    expect(onOpen).toHaveBeenCalledWith(twelve.id);
  });

  it('reloads silently when the screen regains focus', async () => {
    const { element, api } = list();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByText(communitiesText.option(12));

    const reload = jest.mocked(useReloadOnFocus).mock.calls.at(-1)?.[0];
    await act(async () => {
      await reload?.();
    });

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('gives every control a role and a label, targets of 48 px and text of 18 px', async () => {
    const { element } = list();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByText(communitiesText.option(12));

    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBeGreaterThanOrEqual(4);
    for (const button of buttons) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});

describe('community form', () => {
  it('creates a community with a number and a name', async () => {
    const { element, api, onSaved } = edit();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(communitiesText.form.number), '12');
    await fireEvent.changeText(screen.getByLabelText(communitiesText.form.name), '  San Pedro ');
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.form.create }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.create).toHaveBeenCalledWith({ number: 12, name: 'San Pedro' });
    expect(hapticSuccess).toHaveBeenCalled();
  });

  it('omits a blank name', async () => {
    const { element, api } = edit();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(communitiesText.form.number), '7');
    await fireEvent.changeText(screen.getByLabelText(communitiesText.form.name), '   ');
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.form.create }));

    await waitFor(() => expect(api.create).toHaveBeenCalledWith({ number: 7 }));
  });

  it('limits the name to one hundred characters', async () => {
    const { element } = edit();
    await renderAs('ADMINISTRATOR', element);

    expect((await screen.findByLabelText(communitiesText.form.name)).props.maxLength).toBe(100);
  });

  it('uses the numeric keyboard for the number', async () => {
    const { element } = edit();
    await renderAs('ADMINISTRATOR', element);

    expect((await screen.findByLabelText(communitiesText.form.number)).props.keyboardType).toBe('number-pad');
  });

  it('asks for the number without calling the server when it is blank', async () => {
    const { element, api } = edit();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.fieldErrors.REQUIRED}`);
    expect(api.create).not.toHaveBeenCalled();
    expect(hapticFailure).toHaveBeenCalled();
  });

  it.each(['0', '-3', '1.5', 'abc', '99999999999'])('rejects the number %s without calling the server', async (typed) => {
    const { element, api } = edit();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(communitiesText.form.number), typed);
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.fieldErrors.INVALID_FORMAT}`);
    expect(api.create).not.toHaveBeenCalled();
  });

  it('shows the repeated number error next to the number field and keeps what was typed', async () => {
    const api = fakeCommunitiesApi({
      create: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'number', code: 'COMMUNITY_NUMBER_TAKEN' }],
      }),
    });
    const { element, onSaved } = edit(undefined, api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(communitiesText.form.number), '12');
    await fireEvent.changeText(screen.getByLabelText(communitiesText.form.name), 'San Pedro');
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.fieldErrors.COMMUNITY_NUMBER_TAKEN}`);
    expect(screen.getByLabelText(communitiesText.form.number).props.accessibilityHint).toBe(
      communitiesText.fieldErrors.COMMUNITY_NUMBER_TAKEN,
    );
    expect(screen.getByLabelText(communitiesText.form.number).props.value).toBe('12');
    expect(screen.getByLabelText(communitiesText.form.name).props.value).toBe('San Pedro');
    expect(screen.getByLabelText(communitiesText.form.name).props.accessibilityHint).toBeUndefined();
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('shows a name length error next to the name field', async () => {
    const api = fakeCommunitiesApi({
      create: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'name', code: 'TOO_LONG' }],
      }),
    });
    const { element } = edit(undefined, api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(communitiesText.form.number), '12');
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.fieldErrors.TOO_LONG}`);
    expect(screen.getByLabelText(communitiesText.form.name).props.accessibilityHint).toBe(
      communitiesText.fieldErrors.TOO_LONG,
    );
  });

  it('shows a generic message when the request fails', async () => {
    const api = fakeCommunitiesApi({ create: jest.fn().mockRejectedValue(new Error('network down')) });
    const { element } = edit(undefined, api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(communitiesText.form.number), '12');
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.errors.UNKNOWN}`);
  });

  it('does not offer file actions while creating', async () => {
    const { element } = edit();
    await renderAs('ADMINISTRATOR', element);

    await screen.findByLabelText(communitiesText.form.number);
    expect(screen.queryByText(communitiesText.actions.deactivate.row)).not.toBeOnTheScreen();
    expect(screen.queryByText(communitiesText.actions.delete.row)).not.toBeOnTheScreen();
  });
});

describe('community file', () => {
  it('prefills the form with the community', async () => {
    const { element, api } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    expect((await screen.findByLabelText(communitiesText.form.number)).props.value).toBe('12');
    expect(screen.getByLabelText(communitiesText.form.name).props.value).toBe('San Pedro');
    expect(api.get).toHaveBeenCalledWith(twelve.id);
  });

  it('shows an error with a retry when the file cannot be loaded', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, community: detailOf(twelve) });
    const { element } = edit(twelve.id, fakeCommunitiesApi({ get }));
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.file.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.retry }));

    expect(await screen.findByLabelText(communitiesText.form.number)).toBeOnTheScreen();
  });

  it('shows the loading state while the file loads', async () => {
    const { element } = edit(twelve.id, fakeCommunitiesApi({ get: jest.fn().mockReturnValue(new Promise(() => undefined)) }));
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(communitiesText.file.loading)).toBeOnTheScreen();
  });

  it('saves the changes', async () => {
    const { element, api, onSaved } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(communitiesText.form.number), '40');
    await fireEvent.changeText(screen.getByLabelText(communitiesText.form.name), '');
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.form.save }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.update).toHaveBeenCalledWith(twelve.id, { number: 40 });
  });

  it('says that the community has no responsible', async () => {
    const { element } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(communitiesText.list.noResponsible)).toBeOnTheScreen();
  });

  it('asks for confirmation on the web before deactivating, warning about moderators', async () => {
    useWebDialog();
    const { element, api } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.deactivate.row }));

    expect(await screen.findByText(communitiesText.actions.deactivate.warning)).toBeOnTheScreen();
    expect(screen.getByLabelText(communitiesText.actions.deactivate.title(12)).props.role).toBe('alertdialog');
    expect(api.deactivate).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByRole('button', { name: communitiesText.actions.deactivate.confirm }));

    await waitFor(() => expect(api.deactivate).toHaveBeenCalledWith(twelve.id, true));
  });

  it('asks for confirmation natively before deactivating and does nothing when cancelled', async () => {
    const alert = nativeAlert();
    const { element, api } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.deactivate.row }));

    expect(alert).toHaveBeenCalledWith(
      communitiesText.actions.deactivate.title(12),
      communitiesText.actions.deactivate.warning,
      expect.arrayContaining([expect.objectContaining({ style: 'cancel' })]),
    );
    expect(api.deactivate).not.toHaveBeenCalled();
  });

  it('deactivates natively once confirmed', async () => {
    const alert = nativeAlert();
    const { element, api } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.deactivate.row }));
    const buttons = alert.mock.calls[0][2] ?? [];
    await act(async () => {
      buttons.find((button) => button.style === 'destructive')?.onPress?.();
    });

    await waitFor(() => expect(api.deactivate).toHaveBeenCalledWith(twelve.id, true));
  });

  it('shows the reactivation instead of the deactivation for a deactivated community', async () => {
    const api = fakeCommunitiesApi({ get: jest.fn().mockResolvedValue({ ok: true, community: detailOf(closed) }) });
    const { element } = edit(closed.id, api);
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByRole('button', { name: communitiesText.actions.reactivate.row })).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: communitiesText.actions.deactivate.row })).not.toBeOnTheScreen();
  });

  it('reactivates a deactivated community', async () => {
    const api = fakeCommunitiesApi({ get: jest.fn().mockResolvedValue({ ok: true, community: detailOf(closed) }) });
    const { element } = edit(closed.id, api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.reactivate.row }));

    await waitFor(() => expect(api.reactivate).toHaveBeenCalledWith(closed.id));
  });

  it('asks for a definitive deletion confirmation on the web and then deletes', async () => {
    useWebDialog();
    const { element, api, onDeleted } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.delete.row }));

    expect(await screen.findByText(communitiesText.actions.delete.warning)).toBeOnTheScreen();
    expect(api.remove).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByRole('button', { name: communitiesText.actions.delete.confirm }));

    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1));
    expect(api.remove).toHaveBeenCalledWith(twelve.id);
  });

  it('keeps the dialog closed and does not delete when the deletion is cancelled', async () => {
    useWebDialog();
    const { element, api } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.delete.row }));
    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.delete.cancel }));

    await waitFor(() => expect(screen.queryByText(communitiesText.actions.delete.warning)).not.toBeOnTheScreen());
    expect(api.remove).not.toHaveBeenCalled();
  });

  it.each(['COMMUNITY_HAS_RESPONSIBLE', 'COMMUNITY_NOT_EMPTY', 'COMMUNITY_HAS_HISTORY'])(
    'explains why the deletion was refused with %s',
    async (code) => {
      useWebDialog();
      const api = fakeCommunitiesApi({ remove: jest.fn().mockResolvedValue({ ok: false, status: 409, code }) });
      const { element, onDeleted } = edit(twelve.id, api);
      await renderAs('ADMINISTRATOR', element);

      await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.delete.row }));
      await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.delete.confirm }));

      expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.errors[code]}`);
      expect(communitiesText.errors[code]).toBeTruthy();
      expect(onDeleted).not.toHaveBeenCalled();
      expect(hapticFailure).toHaveBeenCalled();
    },
  );

  it('shows the refusal reason of a deactivation', async () => {
    useWebDialog();
    const api = fakeCommunitiesApi({
      deactivate: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'CONCURRENT_MODIFICATION' }),
    });
    const { element } = edit(twelve.id, api);
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.deactivate.row }));
    await fireEvent.press(await screen.findByRole('button', { name: communitiesText.actions.deactivate.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.errors.CONCURRENT_MODIFICATION}`);
  });

  it('gives every control a role and a label, targets of 48 px and text of 18 px', async () => {
    const { element } = edit(twelve.id);
    await renderAs('ADMINISTRATOR', element);
    await screen.findByLabelText(communitiesText.form.number);

    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBeGreaterThanOrEqual(3);
    for (const button of buttons) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    for (const field of [screen.getByLabelText(communitiesText.form.number), screen.getByLabelText(communitiesText.form.name)]) {
      expect(minHeightOf(field)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});
