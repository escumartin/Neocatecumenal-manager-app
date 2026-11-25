import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Dimensions } from 'react-native';
import { DirectoryScreen } from '../index';
import { ana, fakeMembersApi } from '@/screens/members/support/test-support';
import { fakeCommunitiesApi, five, renderAs } from '@/screens/communities/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { communitiesText } from '@/i18n/es/communities';
import { membersText } from '@/i18n/es/members';

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));

afterEach(() => {
  jest.restoreAllMocks();
  jest.clearAllMocks();
});

function directory(api = fakeMembersApi(), communitiesApi = fakeCommunitiesApi()) {
  const onOpen = jest.fn();
  return { api, onOpen, element: <DirectoryScreen api={api} communitiesApi={communitiesApi} onOpen={onOpen} /> };
}

describe('directory screen', () => {
  it.each(['REGISTERED', 'MODERATOR'] as const)('lists the own community of a %s in order', async (role) => {
    const { element, api } = directory();
    await renderAs(role, element);

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
    expect(api.list).toHaveBeenCalledWith(undefined);
    const names = screen.getAllByTestId('list-row').map((row) => row.props.accessibilityLabel as string);
    expect(names[0]).toContain('Luis Álvarez');
    expect(names[1]).toContain('Ana Pérez');
    expect(names[2]).toContain('Marta');
  });

  it('shows the consented mobile and the responsible flag in each row', async () => {
    const { element } = directory();
    await renderAs('REGISTERED', element);

    expect(await screen.findByText('600000001')).toBeOnTheScreen();
    expect(screen.getAllByText(membersText.list.responsible)).toHaveLength(1);
  });

  it('opens the member card when a row is pressed', async () => {
    const { element, onOpen } = directory();
    await renderAs('REGISTERED', element);

    await fireEvent.press(await screen.findByRole('button', { name: /Ana Pérez/ }));

    expect(onOpen).toHaveBeenCalledWith(ana.id);
  });

  it('filters locally without accents or capitals and without asking the server again', async () => {
    const { element, api } = directory();
    await renderAs('REGISTERED', element);
    await screen.findByText('Ana Pérez');

    await fireEvent.changeText(screen.getByLabelText(membersText.directory.searchLabel), 'ALVAREZ');

    expect(screen.getByText('Luis Álvarez')).toBeOnTheScreen();
    expect(screen.queryByText('Ana Pérez')).not.toBeOnTheScreen();
    expect(api.list).toHaveBeenCalledTimes(1);
  });

  it('says so when the search finds nobody and offers to clear it', async () => {
    const { element } = directory();
    await renderAs('REGISTERED', element);
    await screen.findByText('Ana Pérez');

    await fireEvent.changeText(screen.getByLabelText(membersText.directory.searchLabel), 'zzz');

    expect(screen.getByText(membersText.directory.noResults)).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: membersText.directory.clearSearch }));
    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
  });

  it('shows the loading state first', async () => {
    const { element } = directory(fakeMembersApi({ list: jest.fn().mockReturnValue(new Promise(() => undefined)) }));
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(membersText.directory.loading)).toBeOnTheScreen();
  });

  it('shows an empty state when the community has no members', async () => {
    const { element } = directory(fakeMembersApi({ list: jest.fn().mockResolvedValue({ ok: true, members: [] }) }));
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(membersText.directory.empty)).toBeOnTheScreen();
  });

  it('shows an error with a retry that loads again', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, members: [ana] });
    const { element } = directory(fakeMembersApi({ list }));
    await renderAs('REGISTERED', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.directory.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: membersText.retry }));

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
  });

  it('shows a deactivated community without any member data', async () => {
    const { element } = directory(
      fakeMembersApi({ list: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'COMMUNITY_DEACTIVATED' }) }),
    );
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(membersText.directory.deactivated)).toBeOnTheScreen();
    expect(screen.queryByLabelText(membersText.directory.searchLabel)).not.toBeOnTheScreen();
    expect(screen.queryByText('Ana Pérez')).not.toBeOnTheScreen();
  });

  it('lets an administrator choose the community before listing anything', async () => {
    const { element, api } = directory();
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(membersText.directory.chooseCommunity)).toBeOnTheScreen();
    expect(api.list).not.toHaveBeenCalled();

    await fireEvent.press(await screen.findByRole('radio', { name: new RegExp(communitiesText.option(five.number)) }));

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
    expect(api.list).toHaveBeenCalledWith(five.id);
  });

  it('does not offer to choose a community to a registered user', async () => {
    const { element } = directory();
    await renderAs('REGISTERED', element);
    await screen.findByText('Ana Pérez');

    expect(screen.queryByRole('radiogroup')).not.toBeOnTheScreen();
  });

  it('reloads silently when the screen regains focus', async () => {
    const { element, api } = directory();
    await renderAs('REGISTERED', element);
    await screen.findByText('Ana Pérez');

    const reload = jest.mocked(useReloadOnFocus).mock.calls.at(-1)?.[0];
    await act(async () => {
      await reload?.();
    });

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('gives every control a role and a label, targets of 48 px and text of 18 px', async () => {
    const { element } = directory();
    await renderAs('REGISTERED', element);
    await screen.findByText('Ana Pérez');

    for (const button of screen.getAllByRole('button')) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    expect(screen.getByLabelText(membersText.directory.searchLabel).props.className).toContain('min-h-13');
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('keeps every name and flag readable with the font scaled to 200 percent', async () => {
    jest.spyOn(Dimensions, 'get').mockReturnValue({ width: 390, height: 844, scale: 3, fontScale: 2 });
    const { element } = directory();
    await renderAs('REGISTERED', element);

    await waitFor(() => expect(screen.getByText('Luis Álvarez')).toBeOnTheScreen());
    expect(screen.getByText('Ana Pérez')).toBeOnTheScreen();
    expect(screen.getByText('Marta')).toBeOnTheScreen();
    expect(screen.getByText(membersText.list.responsible)).toBeOnTheScreen();
  });
});
