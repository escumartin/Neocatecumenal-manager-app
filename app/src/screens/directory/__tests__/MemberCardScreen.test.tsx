import { fireEvent, screen } from '@testing-library/react-native';
import { Linking } from 'react-native';
import { MemberCardScreen } from '../member-card';
import { ana, fakeMembersApi, luis, marta } from '@/screens/members/support/test-support';
import { renderAs } from '@/screens/communities/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { membersText } from '@/i18n/es/members';
import type { Member } from '@/lib/api/members-api';

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));

const everything: Member = {
  ...ana,
  landline: '600000003',
  email: 'ana@example.org',
};

beforeEach(() => {
  jest.spyOn(Linking, 'openURL').mockResolvedValue();
});

afterEach(() => {
  jest.restoreAllMocks();
  jest.clearAllMocks();
});

function card(
  member: Member = everything,
  api = fakeMembersApi({ get: jest.fn().mockResolvedValue({ ok: true, member }) }),
) {
  return { api, element: <MemberCardScreen memberId={member.id} api={api} /> };
}

describe('member card screen', () => {
  it('shows the full name and the responsible flag', async () => {
    const { element } = card(luis);
    await renderAs('REGISTERED', element);

    expect(await screen.findByRole('heading', { name: 'Luis Álvarez' })).toBeOnTheScreen();
    expect(screen.getByText(membersText.list.responsible)).toBeOnTheScreen();
  });

  it('shows the consented contacts with an action for each', async () => {
    const { element } = card();
    await renderAs('REGISTERED', element);

    expect(await screen.findByText('600000001')).toBeOnTheScreen();
    expect(screen.getByText('600000003')).toBeOnTheScreen();
    expect(screen.getByText('ana@example.org')).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: membersText.directory.callMobile('Ana Pérez') })).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: membersText.directory.textMobile('Ana Pérez') })).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: membersText.directory.callLandline('Ana Pérez') })).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: membersText.directory.writeEmail('Ana Pérez') })).toBeOnTheScreen();
  });

  it('calls, texts and writes through the system with the right links', async () => {
    const { element } = card();
    await renderAs('REGISTERED', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.directory.callMobile('Ana Pérez') }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.directory.textMobile('Ana Pérez') }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.directory.callLandline('Ana Pérez') }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.directory.writeEmail('Ana Pérez') }));

    expect(Linking.openURL).toHaveBeenNthCalledWith(1, 'tel:600000001');
    expect(Linking.openURL).toHaveBeenNthCalledWith(2, 'sms:600000001');
    expect(Linking.openURL).toHaveBeenNthCalledWith(3, 'tel:600000003');
    expect(Linking.openURL).toHaveBeenNthCalledWith(4, 'mailto:ana@example.org');
  });

  it('offers actions only for the contacts that are present', async () => {
    const { element } = card(marta);
    await renderAs('REGISTERED', element);

    expect(await screen.findByText('marta@example.org')).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: membersText.directory.writeEmail('Marta') })).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: membersText.directory.callMobile('Marta') })).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: membersText.directory.callLandline('Marta') })).not.toBeOnTheScreen();
  });

  it('says there is no shared contact when the member shares nothing', async () => {
    const { element } = card(luis);
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(membersText.directory.noContact)).toBeOnTheScreen();
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });

  it('shows the loading state first', async () => {
    const { element } = card(ana, fakeMembersApi({ get: jest.fn().mockReturnValue(new Promise(() => undefined)) }));
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(membersText.directory.cardLoading)).toBeOnTheScreen();
  });

  it('shows an error with a retry that loads again', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, member: ana });
    const { element } = card(ana, fakeMembersApi({ get }));
    await renderAs('REGISTERED', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.directory.cardLoadError}`);
    await fireEvent.press(screen.getByRole('button', { name: membersText.retry }));

    expect(await screen.findByRole('heading', { name: 'Ana Pérez' })).toBeOnTheScreen();
  });

  it.each([403, 404])('shows an empty state without retry when the server answers %s', async (status) => {
    const { element } = card(ana, fakeMembersApi({ get: jest.fn().mockResolvedValue({ ok: false, status }) }));
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(membersText.directory.cardUnavailable)).toBeOnTheScreen();
    expect(screen.queryByRole('alert')).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: membersText.retry })).not.toBeOnTheScreen();
  });

  it('gives every control a role and a label, targets of 48 px and text of 18 px', async () => {
    const { element } = card();
    await renderAs('REGISTERED', element);
    await screen.findByText('600000001');

    for (const button of screen.getAllByRole('button')) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});
