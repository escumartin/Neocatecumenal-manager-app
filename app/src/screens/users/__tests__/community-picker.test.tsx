import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { CommunityPicker } from '../community-picker';
import { SessionProvider, type SessionApi } from '@/hooks/session/use-session';
import type { CommunitiesApi } from '@/lib/api/communities-api';
import { communitiesText } from '@/i18n/es/communities';

const communities = [
  { id: '6f1c1f1e-0000-4000-8000-000000000011', number: 1 },
  { id: '6f1c1f1e-0000-4000-8000-000000000012', number: 2 },
];

function me(role: string) {
  return {
    userId: '6f1c1f1e-0000-4000-8000-000000000001',
    accessId: 482913,
    name: 'Test User',
    role,
    communityId: communities[0].id,
    communityNumber: 1,
    pendingSteps: [],
    currentPrivacyNoticeVersion: '1',
  };
}

async function renderPicker(role: string, list: CommunitiesApi['list'], selectedId: string | null = null) {
  const onSelect = jest.fn();
  const sessionApi = {
    logIn: jest.fn(),
    logOut: jest.fn(),
    getMe: jest.fn().mockResolvedValue({ ok: true, me: me(role) }),
  } as unknown as SessionApi;
  const store = { read: jest.fn().mockResolvedValue(null), save: jest.fn(), clear: jest.fn() };
  await render(
    <SessionProvider api={sessionApi} store={store}>
      <CommunityPicker api={{ list }} selectedId={selectedId} onSelect={onSelect} />
    </SessionProvider>,
  );
  return onSelect;
}

describe('community picker', () => {
  it('lists every community as a labelled option for an administrator', async () => {
    await renderPicker('ADMINISTRATOR', jest.fn().mockResolvedValue({ ok: true, communities }));

    expect(await screen.findByRole('radio', { name: communitiesText.option(1) })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: communitiesText.option(2) })).toBeOnTheScreen();
    expect(screen.getByText(communitiesText.pickerTitle)).toBeOnTheScreen();
  });

  it('reports the chosen community', async () => {
    const onSelect = await renderPicker('ADMINISTRATOR', jest.fn().mockResolvedValue({ ok: true, communities }));

    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));

    expect(onSelect).toHaveBeenCalledWith(communities[1].id);
  });

  it('marks the selected community without relying on colour', async () => {
    await renderPicker('ADMINISTRATOR', jest.fn().mockResolvedValue({ ok: true, communities }), communities[1].id);

    const selected = await screen.findByRole('radio', { name: communitiesText.option(2) });
    expect(selected).toBeChecked();
    expect(screen.getByRole('radio', { name: communitiesText.option(1) })).not.toBeChecked();
    expect(screen.getByText(`✓ ${communitiesText.option(2)}`)).toBeOnTheScreen();
  });

  it('renders nothing and does not load communities for a moderator', async () => {
    const list = jest.fn();
    await renderPicker('MODERATOR', list);

    await waitFor(() => expect(screen.queryByText(communitiesText.pickerTitle)).not.toBeOnTheScreen());
    expect(list).not.toHaveBeenCalled();
  });

  it('shows a retry action when the communities cannot be loaded', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, communities });
    await renderPicker('ADMINISTRATOR', list);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${communitiesText.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: communitiesText.retry }));

    expect(await screen.findByRole('radio', { name: communitiesText.option(1) })).toBeOnTheScreen();
  });
});
