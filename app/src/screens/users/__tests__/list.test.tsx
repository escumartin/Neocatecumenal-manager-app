import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Alert, Platform } from 'react-native';
import { UserList } from '../list';
import { ana, communities, communitiesApi, fakeUsersApi, luis, ownUserId, renderAs } from '../support/test-support';
import { communitiesText } from '@/i18n/es/communities';
import { usersText } from '@/i18n/es/users';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { hapticFailure } from '@/lib/haptics';

function list(api = fakeUsersApi()) {
  const onCreate = jest.fn();
  const onEdit = jest.fn();
  const element = <UserList api={api} communitiesApi={communitiesApi} onCreate={onCreate} onEdit={onEdit} />;
  return { element, api, onCreate, onEdit };
}

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));
jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

function row(name: string) {
  return screen.findByRole('button', { name: new RegExp(name) });
}

async function startDeletion(name: string) {
  await fireEvent(await row(name), 'longPress');
}

function useWebDialog() {
  jest.replaceProperty(Platform, 'OS', 'web');
}

function nativeAlert() {
  return jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
}

const mockedReloadOnFocus = jest.mocked(useReloadOnFocus);

async function regainFocus() {
  const reload = mockedReloadOnFocus.mock.calls.at(-1)?.[0];
  await act(async () => {
    await reload?.();
  });
}

describe('user list', () => {
  it('shows each user with name, access id and role', async () => {
    const { element } = list();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText('Ana')).toBeOnTheScreen();
    expect(screen.getByText('Luis')).toBeOnTheScreen();
    expect(screen.getAllByText(usersText.roles.REGISTERED)).toHaveLength(2);
    expect(screen.getByText(usersText.accessId(482913))).toBeOnTheScreen();
  });

  it('renders a long list lazily instead of all rows at once', async () => {
    const many = Array.from({ length: 200 }, (_, index) => ({
      ...ana,
      id: `6f1c1f1e-0000-4000-8000-${String(index).padStart(12, '0')}`,
      accessId: 100000 + index,
      name: `Person ${index}`,
    }));
    const { element } = list(fakeUsersApi({ list: jest.fn().mockResolvedValue({ ok: true, users: many }) }));
    await renderAs('MODERATOR', element);

    await screen.findByText('Person 0');

    expect(screen.queryByText('Person 199')).not.toBeOnTheScreen();
  });

  it('loads the community of the moderator without offering a community choice', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);

    await screen.findByText('Ana');
    expect(api.list).toHaveBeenCalledWith(communities[0].id);
    expect(screen.queryByText(communitiesText.pickerTitle)).not.toBeOnTheScreen();
  });

  it('lets an administrator pick the community whose users are listed', async () => {
    const { element, api } = list();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith(communities[1].id));
  });

  it('shows an empty state when the community has no users', async () => {
    const { element } = list(fakeUsersApi({ list: jest.fn().mockResolvedValue({ ok: true, users: [] }) }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(usersText.empty)).toBeOnTheScreen();
  });

  it('shows an error with a retry action when the list cannot be loaded', async () => {
    const api = fakeUsersApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: false, status: 500 })
        .mockResolvedValueOnce({ ok: true, users: [ana] }),
    });
    const { element } = list(api);
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: usersText.retry }));

    expect(await screen.findByText('Ana')).toBeOnTheScreen();
  });

  it('starts the creation of a user', async () => {
    const { element, onCreate } = list();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: usersText.create }));

    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it('opens the edition of the chosen user by pressing its row', async () => {
    const { element, onEdit } = list();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await row('Luis'));

    expect(onEdit).toHaveBeenCalledWith(luis.id);
  });

  it('does not repeat the screen title inside the content', async () => {
    const { element } = list();
    await renderAs('MODERATOR', element);

    await screen.findByText('Ana');

    expect(screen.queryByText(usersText.title)).not.toBeOnTheScreen();
  });

  it('has no inline edit or delete buttons on the rows', async () => {
    const { element } = list();
    await renderAs('MODERATOR', element);

    await screen.findByText('Ana');

    expect(screen.queryByRole('button', { name: usersText.deleteFor('Ana') })).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: usersText.editFor('Ana') })).not.toBeOnTheScreen();
  });

  it('tells how to delete a user in the footer of the list', async () => {
    const { element } = list();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(usersText.deleteTip)).toBeOnTheScreen();
    expect(usersText.deleteTip).toContain(usersText.delete.row);
    expect(usersText.deleteTip).toContain('mantén pulsado');
  });

  it('shows a spinner while the users load', async () => {
    const api = fakeUsersApi({ list: jest.fn().mockReturnValue(new Promise(() => undefined)) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);

    expect(await screen.findByTestId('state-view-spinner')).toBeOnTheScreen();
    expect(screen.getByText(usersText.loading)).toBeOnTheScreen();
  });

  it('reloads the users on pull to refresh', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);
    await screen.findByText('Ana');

    await act(async () => {
      await screen.getByTestId('user-list').props.refreshControl.props.onRefresh();
    });

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('asks with a native alert before deleting and does not call the server yet', async () => {
    const alert = nativeAlert();
    const { element, api } = list();
    await renderAs('MODERATOR', element);

    await startDeletion('Ana');

    expect(alert).toHaveBeenCalledWith(
      usersText.delete.title('Ana'),
      usersText.delete.warning,
      expect.arrayContaining([expect.objectContaining({ text: usersText.delete.confirm, style: 'destructive' })]),
    );
    expect(api.remove).not.toHaveBeenCalled();
  });

  it('offers deletion as an accessibility action of the row', async () => {
    const alert = nativeAlert();
    const { element } = list();
    await renderAs('MODERATOR', element);

    await fireEvent(await row('Ana'), 'accessibilityAction', { nativeEvent: { actionName: 'delete' } });

    expect(alert).toHaveBeenCalledTimes(1);
    expect((await row('Ana')).props.accessibilityActions).toEqual([
      { name: 'delete', label: usersText.deleteFor('Ana') },
    ]);
  });

  it('removes the user once the native alert is confirmed', async () => {
    const alert = nativeAlert();
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: true }) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);
    await startDeletion('Ana');

    await act(async () => {
      alert.mock.calls[0][2]?.find((button) => button.style === 'destructive')?.onPress?.();
    });

    await waitFor(() => expect(screen.queryByText('Ana')).not.toBeOnTheScreen());
    expect(api.remove).toHaveBeenCalledWith(ana.id);
  });

  it('shows why the deletion failed after confirming the native alert', async () => {
    const alert = nativeAlert();
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'LAST_MODERATOR' }) });
    const { element } = list(api);
    await renderAs('ADMINISTRATOR', element);
    await startDeletion('Ana');

    await act(async () => {
      alert.mock.calls[0][2]?.find((button) => button.style === 'destructive')?.onPress?.();
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.LAST_MODERATOR}`);
  });

  it('asks for confirmation in a dialog on web and does not call the server yet', async () => {
    useWebDialog();
    const { element, api } = list();
    await renderAs('MODERATOR', element);

    await startDeletion('Ana');

    expect(screen.getByLabelText(usersText.delete.title('Ana'))).toBeOnTheScreen();
    expect(api.remove).not.toHaveBeenCalled();
  });

  it('keeps the user when the deletion is cancelled', async () => {
    useWebDialog();
    const { element, api } = list();
    await renderAs('MODERATOR', element);
    await startDeletion('Ana');

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.cancel }));

    expect(screen.queryByLabelText(usersText.delete.title('Ana'))).not.toBeOnTheScreen();
    expect(screen.getByText('Ana')).toBeOnTheScreen();
    expect(api.remove).not.toHaveBeenCalled();
  });

  it('removes the user from the list once the dialog is confirmed', async () => {
    useWebDialog();
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: true }) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);
    await startDeletion('Ana');

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    await waitFor(() => expect(screen.queryByText('Ana')).not.toBeOnTheScreen());
    expect(api.remove).toHaveBeenCalledWith(ana.id);
    expect(screen.queryByLabelText(usersText.delete.title('Ana'))).not.toBeOnTheScreen();
  });

  it('explains why the last moderator cannot be deleted', async () => {
    useWebDialog();
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'LAST_MODERATOR' }) });
    const { element } = list(api);
    await renderAs('ADMINISTRATOR', element);
    await startDeletion('Ana');

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.LAST_MODERATOR}`);
    expect(screen.getByText('Ana')).toBeOnTheScreen();
  });

  it('explains that the own account cannot be deleted', async () => {
    useWebDialog();
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'SELF_ACTION_FORBIDDEN' }) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);
    await startDeletion('Ana');

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.SELF_ACTION_FORBIDDEN}`);
  });

  it('gives haptic feedback on the result of a deletion', async () => {
    useWebDialog();
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'LAST_MODERATOR' }) });
    const { element } = list(api);
    await renderAs('ADMINISTRATOR', element);
    await startDeletion('Ana');

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    await waitFor(() => expect(hapticFailure).toHaveBeenCalledTimes(1));
  });

  it('offers deletion by long press only on accounts the user can manage', async () => {
    const alert = nativeAlert();
    const own = { ...ana, id: ownUserId, name: 'Yo', role: 'MODERATOR' as const };
    const otherModerator = { ...luis, name: 'Marta', role: 'MODERATOR' as const };
    const api = fakeUsersApi({ list: jest.fn().mockResolvedValue({ ok: true, users: [own, otherModerator, ana] }) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);

    await startDeletion('Yo');
    await startDeletion('Marta');
    expect(alert).not.toHaveBeenCalled();
    expect((await row('Yo')).props.accessibilityActions).toBeUndefined();
    expect((await row('Marta')).props.accessibilityActions).toBeUndefined();

    await startDeletion('Ana');
    expect(alert).toHaveBeenCalledTimes(1);
    expect((await row('Ana')).props.accessibilityActions).toHaveLength(1);
  });

  it('lets an administrator delete a moderator but not the own account', async () => {
    const alert = nativeAlert();
    const own = { ...ana, id: ownUserId, name: 'Yo', role: 'ADMINISTRATOR' as const };
    const moderator = { ...luis, name: 'Marta', role: 'MODERATOR' as const };
    const api = fakeUsersApi({ list: jest.fn().mockResolvedValue({ ok: true, users: [own, moderator] }) });
    const { element } = list(api);
    await renderAs('ADMINISTRATOR', element);

    await startDeletion('Yo');
    expect(alert).not.toHaveBeenCalled();

    await startDeletion('Marta');
    expect(alert).toHaveBeenCalledTimes(1);
  });

  it('hints that a manageable row can be held to delete', async () => {
    const { element } = list();
    await renderAs('MODERATOR', element);

    expect(usersText.deleteHint).toEqual(expect.any(String));
    expect((await row('Ana')).props.accessibilityHint).toBe(usersText.deleteHint);
  });

  it('explains an unexpected failure while deleting', async () => {
    useWebDialog();
    const api = fakeUsersApi({ remove: jest.fn().mockRejectedValue(new Error('network down')) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);
    await startDeletion('Ana');

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.UNKNOWN}`);
    expect(hapticFailure).toHaveBeenCalledTimes(1);
    expect(screen.queryByLabelText(usersText.delete.title('Ana'))).not.toBeOnTheScreen();
    expect(screen.getByText('Ana')).toBeOnTheScreen();
  });

  it('explains an unexpected failure after confirming the native alert', async () => {
    const alert = nativeAlert();
    const api = fakeUsersApi({ remove: jest.fn().mockRejectedValue(new Error('network down')) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);
    await startDeletion('Ana');

    await act(async () => {
      alert.mock.calls[0][2]?.find((button) => button.style === 'destructive')?.onPress?.();
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.UNKNOWN}`);
  });

  it('reloads the users when the screen regains focus', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);
    await screen.findByText('Ana');

    await regainFocus();

    expect(api.list).toHaveBeenCalledTimes(2);
  });
});
