import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Alert, Platform } from 'react-native';
import { MemberForm } from '../form';
import { MemberLink } from '../link';
import { MemberTransfer } from '../transfer';
import { ana, fakeMembersApi, linked } from '../support/test-support';
import { fakeCommunitiesApi, five, renderAs, twelve } from '@/screens/communities/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { fakeUsersApi } from '@/screens/users/support/test-support';
import { communitiesText } from '@/i18n/es/communities';
import { membersText } from '@/i18n/es/members';
import type { User } from '@/lib/api/users-api';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));
jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

function userOf(id: string, name: string, role: User['role']): User {
  return { id, accessId: 480000 + Number(id.slice(-2)), name, role, communityId: twelve.id, passwordChangePending: false };
}

const berta = userOf('6f1c1f1e-0000-4000-8000-000000000051', 'Berta', 'REGISTERED');
const carlos = userOf('6f1c1f1e-0000-4000-8000-000000000052', 'Carlos', 'REGISTERED');
const moderator = userOf('6f1c1f1e-0000-4000-8000-000000000053', 'Marcos', 'MODERATOR');
const administrator = userOf('6f1c1f1e-0000-4000-8000-000000000054', 'Adela', 'ADMINISTRATOR');

function usersApi(users: User[] = [moderator, berta, administrator, carlos]) {
  return fakeUsersApi({ list: jest.fn().mockResolvedValue({ ok: true, users }) });
}

function linkScreen(api = fakeMembersApi(), users = usersApi()) {
  const onLinked = jest.fn();
  return {
    api,
    users,
    onLinked,
    element: <MemberLink memberId={ana.id} api={api} usersApi={users} onLinked={onLinked} />,
  };
}

function transferScreen(api = fakeMembersApi(), communitiesApi = fakeCommunitiesApi()) {
  const onTransferred = jest.fn();
  return {
    api,
    communitiesApi,
    onTransferred,
    element: (
      <MemberTransfer memberId={ana.id} api={api} communitiesApi={communitiesApi} onTransferred={onTransferred} />
    ),
  };
}

function memberForm(api = fakeMembersApi(), memberId = ana.id) {
  const onOpenLink = jest.fn();
  const onOpenTransfer = jest.fn();
  return {
    api,
    onOpenLink,
    onOpenTransfer,
    element: (
      <MemberForm
        memberId={memberId}
        api={api}
        communitiesApi={fakeCommunitiesApi()}
        onSaved={jest.fn()}
        onOpenContact={jest.fn()}
        onOpenLink={onOpenLink}
        onOpenTransfer={onOpenTransfer}
      />
    ),
  };
}

describe('link screen', () => {
  it('offers only the registered users of the community of the member', async () => {
    const { element, users } = linkScreen();
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('radio', { name: 'Berta' })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: 'Carlos' })).toBeOnTheScreen();
    expect(screen.queryByRole('radio', { name: 'Marcos' })).not.toBeOnTheScreen();
    expect(screen.queryByRole('radio', { name: 'Adela' })).not.toBeOnTheScreen();
    expect(users.list).toHaveBeenCalledWith(twelve.id);
  });

  it('links the chosen user, gives haptic feedback and goes back', async () => {
    const { element, api, onLinked } = linkScreen();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: 'Carlos' }));
    expect(screen.getByRole('radio', { name: 'Carlos' })).toBeChecked();
    await fireEvent.press(screen.getByRole('button', { name: membersText.link.confirm }));

    await waitFor(() => expect(onLinked).toHaveBeenCalledTimes(1));
    expect(api.link).toHaveBeenCalledWith(ana.id, carlos.id);
    expect(hapticSuccess).toHaveBeenCalledTimes(1);
  });

  it('asks to choose a user without calling the server', async () => {
    const { element, api } = linkScreen();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.link.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.link.chooseUser}`);
    expect(api.link).not.toHaveBeenCalled();
    expect(hapticFailure).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['MEMBER_ALREADY_LINKED', 409, undefined],
    ['USER_ALREADY_LINKED', 409, undefined],
    ['FORBIDDEN', 403, undefined],
    ['CONCURRENT_MODIFICATION', 409, undefined],
    ['USER_NOT_IN_COMMUNITY', 422, [{ field: 'userId', code: 'USER_NOT_IN_COMMUNITY' }]],
    ['USER_NOT_FOUND', 422, [{ field: 'userId', code: 'USER_NOT_FOUND' }]],
  ])('explains %s and stays on the screen', async (code, status, fieldErrors) => {
    const link = jest.fn().mockResolvedValue({ ok: false, status, code, ...(fieldErrors ? { fieldErrors } : {}) });
    const { element, onLinked } = linkScreen(fakeMembersApi({ link }));
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: 'Berta' }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.link.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors[code]}`);
    expect(onLinked).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: membersText.link.confirm })).not.toBeDisabled();
  });

  it('explains a failure of the request itself', async () => {
    const link = jest.fn().mockRejectedValue(new Error('network down'));
    const { element } = linkScreen(fakeMembersApi({ link }));
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: 'Berta' }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.link.confirm }));

    expect(await screen.findByText(`⚠ ${membersText.errors.UNKNOWN}`)).toBeOnTheScreen();
  });

  it('shows an empty state when the community has no registered users', async () => {
    const { element } = linkScreen(fakeMembersApi(), usersApi([moderator]));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.link.empty)).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: membersText.link.confirm })).not.toBeOnTheScreen();
  });

  it('shows the loading state while the users arrive', async () => {
    const pending = fakeUsersApi({ list: jest.fn().mockReturnValue(new Promise(() => undefined)) });
    const { element } = linkScreen(fakeMembersApi(), pending);
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.link.loading)).toBeOnTheScreen();
  });

  it('shows an error with a retry when the users cannot be loaded', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, users: [berta] });
    const { element } = linkScreen(fakeMembersApi(), fakeUsersApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.link.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: membersText.retry }));

    expect(await screen.findByRole('radio', { name: 'Berta' })).toBeOnTheScreen();
  });

  it('shows an error when the member cannot be loaded', async () => {
    const get = jest.fn().mockResolvedValue({ ok: false, status: 404, code: 'NOT_FOUND' });
    const { element } = linkScreen(fakeMembersApi({ get }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.form.loadError}`);
  });

  it('keeps controls labelled, at least 48 px tall and texts at least 18 px', async () => {
    const { element } = linkScreen();
    await renderAs('MODERATOR', element);
    await fireEvent.press(await screen.findByRole('radio', { name: 'Berta' }));

    for (const control of [...screen.getAllByRole('button'), ...screen.getAllByRole('radio')]) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});

describe('transfer screen', () => {
  it('tells a moderator that only administrators transfer and offers nothing to choose', async () => {
    const { element, communitiesApi } = transferScreen();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.transfer.administratorsOnly)).toBeOnTheScreen();
    expect(screen.queryByRole('radiogroup')).not.toBeOnTheScreen();
    expect(communitiesApi.list).not.toHaveBeenCalled();
  });

  it('offers the other active communities to an administrator', async () => {
    const { element } = transferScreen();
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByRole('radio', { name: communitiesText.option(five.number) })).toBeOnTheScreen();
    expect(screen.queryByRole('radio', { name: communitiesText.option(twelve.number) })).not.toBeOnTheScreen();
    expect(screen.queryByRole('radio', { name: communitiesText.option(3) })).not.toBeOnTheScreen();
  });

  it('asks to choose a community without calling the server', async () => {
    const { element, api } = transferScreen();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.transfer.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.transfer.chooseCommunity}`);
    expect(api.transfer).not.toHaveBeenCalled();
  });

  it('asks with a native alert before transferring', async () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const { element, api } = transferScreen();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(five.number) }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.confirm }));

    expect(alert).toHaveBeenCalledWith(
      membersText.transfer.dialog.title('Ana Pérez', five.number),
      membersText.transfer.dialog.warning,
      expect.arrayContaining([expect.objectContaining({ text: membersText.transfer.dialog.confirm })]),
    );
    expect(api.transfer).not.toHaveBeenCalled();
  });

  it('transfers once the native alert is confirmed', async () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const { element, api, onTransferred } = transferScreen();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(five.number) }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.confirm }));
    const buttons = alert.mock.calls[0][2] ?? [];
    await act(async () => buttons.find((button) => button.text === membersText.transfer.dialog.confirm)?.onPress?.());

    await waitFor(() => expect(onTransferred).toHaveBeenCalledTimes(1));
    expect(api.transfer).toHaveBeenCalledWith(ana.id, five.id);
    expect(hapticSuccess).toHaveBeenCalledTimes(1);
  });

  it('asks with an accessible dialog on web and transfers once confirmed', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const { element, api, onTransferred } = transferScreen();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(five.number) }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.confirm }));
    expect(screen.getByLabelText(membersText.transfer.dialog.title('Ana Pérez', five.number)).props.role).toBe(
      'alertdialog',
    );
    expect(api.transfer).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.dialog.confirm }));

    await waitFor(() => expect(onTransferred).toHaveBeenCalledTimes(1));
    expect(api.transfer).toHaveBeenCalledWith(ana.id, five.id);
  });

  it('keeps the member where it is when the web dialog is cancelled', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const { element, api, onTransferred } = transferScreen();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(five.number) }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.confirm }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.dialog.cancel }));

    expect(api.transfer).not.toHaveBeenCalled();
    expect(onTransferred).not.toHaveBeenCalled();
  });

  it.each([
    ['COMMUNITY_DEACTIVATED', 422, [{ field: 'communityId', code: 'COMMUNITY_DEACTIVATED' }]],
    ['RESPONSIBLE_REQUIRED', 409, undefined],
    ['LAST_MODERATOR', 409, undefined],
    ['USER_LINKED_TO_MEMBER', 409, undefined],
    ['FORBIDDEN', 403, undefined],
  ])('explains %s and stays on the screen', async (code, status, fieldErrors) => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const transfer = jest.fn().mockResolvedValue({ ok: false, status, code, ...(fieldErrors ? { fieldErrors } : {}) });
    const { element, onTransferred } = transferScreen(fakeMembersApi({ transfer }));
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(five.number) }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.confirm }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.transfer.dialog.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors[code]}`);
    expect(onTransferred).not.toHaveBeenCalled();
    expect(hapticFailure).toHaveBeenCalledTimes(1);
  });

  it('shows an empty state when there is no other active community', async () => {
    const onlyOwn = fakeCommunitiesApi({ list: jest.fn().mockResolvedValue({ ok: true, communities: [twelve] }) });
    const { element } = transferScreen(fakeMembersApi(), onlyOwn);
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(membersText.transfer.empty)).toBeOnTheScreen();
  });

  it('keeps controls labelled, at least 48 px tall and texts at least 18 px', async () => {
    const { element } = transferScreen();
    await renderAs('ADMINISTRATOR', element);
    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(five.number) }));

    for (const control of [...screen.getAllByRole('button'), ...screen.getAllByRole('radio')]) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});

describe('member file actions', () => {
  it('lets a moderator link an unlinked member but does not show the transfer action', async () => {
    const { element, onOpenLink } = memberForm();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.link.row }));

    expect(onOpenLink).toHaveBeenCalledWith(ana.id);
    expect(screen.queryByRole('button', { name: membersText.transfer.row })).not.toBeOnTheScreen();
  });

  it('lets an administrator open the transfer screen', async () => {
    const { element, onOpenTransfer } = memberForm();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.transfer.row }));

    expect(onOpenTransfer).toHaveBeenCalledWith(ana.id);
  });

  it('offers to unlink a linked member instead of linking it', async () => {
    const { element } = memberForm(fakeMembersApi({ get: jest.fn().mockResolvedValue({ ok: true, member: linked }) }), linked.id);
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('button', { name: membersText.link.unlink.row })).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: membersText.link.row })).not.toBeOnTheScreen();
  });

  it('unlinks after the confirmation and reloads the member', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: true, member: linked })
      .mockResolvedValueOnce({ ok: true, member: { ...linked, linkedUserId: undefined } });
    const { element, api } = memberForm(fakeMembersApi({ get }), linked.id);
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.link.unlink.row }));
    expect(api.unlink).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByRole('button', { name: membersText.link.unlink.confirm }));

    await waitFor(() => expect(api.unlink).toHaveBeenCalledWith(linked.id));
    expect(await screen.findByRole('button', { name: membersText.link.row })).toBeOnTheScreen();
  });

  it('explains a refused unlink and keeps the link', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const unlink = jest.fn().mockResolvedValue({ ok: false, status: 403, code: 'FORBIDDEN' });
    const { element } = memberForm(
      fakeMembersApi({ get: jest.fn().mockResolvedValue({ ok: true, member: linked }), unlink }),
      linked.id,
    );
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.link.unlink.row }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.link.unlink.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.FORBIDDEN}`);
    expect(screen.getByRole('button', { name: membersText.link.unlink.row })).toBeOnTheScreen();
  });
});
