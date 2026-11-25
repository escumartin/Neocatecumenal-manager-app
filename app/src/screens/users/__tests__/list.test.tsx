import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { UserList } from '../list';
import { ana, communities, communitiesApi, fakeUsersApi, luis, renderAs } from '../support/test-support';
import { communitiesText } from '@/i18n/es/communities';
import { usersText } from '@/i18n/es/users';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';

function list(api = fakeUsersApi()) {
  const onCreate = jest.fn();
  const onEdit = jest.fn();
  const element = <UserList api={api} communitiesApi={communitiesApi} onCreate={onCreate} onEdit={onEdit} />;
  return { element, api, onCreate, onEdit };
}

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));

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

  it('opens the edition of the chosen user', async () => {
    const { element, onEdit } = list();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: usersText.editFor('Luis') }));

    expect(onEdit).toHaveBeenCalledWith(luis.id);
  });

  it('asks for confirmation before deleting and does not call the server yet', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: usersText.deleteFor('Ana') }));

    expect(screen.getByLabelText(usersText.delete.title('Ana'))).toBeOnTheScreen();
    expect(api.remove).not.toHaveBeenCalled();
  });

  it('keeps the user when the deletion is cancelled', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);
    await fireEvent.press(await screen.findByRole('button', { name: usersText.deleteFor('Ana') }));

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.cancel }));

    expect(screen.queryByLabelText(usersText.delete.title('Ana'))).not.toBeOnTheScreen();
    expect(screen.getByText('Ana')).toBeOnTheScreen();
    expect(api.remove).not.toHaveBeenCalled();
  });

  it('removes the user from the list once the deletion is confirmed', async () => {
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: true }) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);
    await fireEvent.press(await screen.findByRole('button', { name: usersText.deleteFor('Ana') }));

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    await waitFor(() => expect(screen.queryByText('Ana')).not.toBeOnTheScreen());
    expect(api.remove).toHaveBeenCalledWith(ana.id);
    expect(screen.queryByLabelText(usersText.delete.title('Ana'))).not.toBeOnTheScreen();
  });

  it('explains why the last moderator cannot be deleted', async () => {
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'LAST_MODERATOR' }) });
    const { element } = list(api);
    await renderAs('ADMINISTRATOR', element);
    await fireEvent.press(await screen.findByRole('button', { name: usersText.deleteFor('Ana') }));

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.LAST_MODERATOR}`);
    expect(screen.getByText('Ana')).toBeOnTheScreen();
  });

  it('explains that the own account cannot be deleted', async () => {
    const api = fakeUsersApi({ remove: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'SELF_ACTION_FORBIDDEN' }) });
    const { element } = list(api);
    await renderAs('MODERATOR', element);
    await fireEvent.press(await screen.findByRole('button', { name: usersText.deleteFor('Ana') }));

    await fireEvent.press(screen.getByRole('button', { name: usersText.delete.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${usersText.errors.SELF_ACTION_FORBIDDEN}`);
  });

  it('reloads the users when the screen regains focus', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);
    await screen.findByText('Ana');

    await regainFocus();

    expect(api.list).toHaveBeenCalledTimes(2);
  });
});
