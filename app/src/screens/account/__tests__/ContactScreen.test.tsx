import { Dimensions } from 'react-native';
import { fireEvent, screen, waitFor } from '@testing-library/react-native';
import { ContactScreen } from '../contact';
import type { MyMember, MyMemberApi } from '@/lib/api/members-api';
import { membersText } from '@/i18n/es/members';
import { communitiesText } from '@/i18n/es/communities';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { renderAs, twelve } from '@/screens/communities/support/test-support';
import { ana } from '@/screens/members/support/test-support';

jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

const withoutContact = { ...ana, mobile: undefined };

const nothingShared: MyMember = { member: withoutContact, currentConsents: [] };

const recordedByModerator: MyMember = {
  member: ana,
  currentConsents: [
    {
      field: 'MOBILE',
      decision: 'GRANTED',
      channel: 'VERBAL',
      recordedBy: { role: 'MODERATOR', name: 'Rosa Gómez', deleted: false },
      noticeGiven: true,
      recordedAt: '2026-11-20T10:00:00Z',
    },
  ],
};

const recordedByDeletedModerator: MyMember = {
  member: ana,
  currentConsents: [
    {
      field: 'MOBILE',
      decision: 'GRANTED',
      channel: 'WRITTEN',
      recordedBy: { role: 'MODERATOR', deleted: true },
      noticeGiven: true,
      recordedAt: '2026-11-20T10:00:00Z',
    },
  ],
};

const recordedByMember: MyMember = {
  member: ana,
  currentConsents: [
    {
      field: 'MOBILE',
      decision: 'GRANTED',
      channel: 'APP',
      recordedBy: { role: 'MEMBER', deleted: false },
      noticeGiven: true,
      recordedAt: '2026-11-20T10:00:00Z',
    },
  ],
};

function fakeApi(mine: MyMember = nothingShared, overrides: Partial<MyMemberApi> = {}) {
  const api: MyMemberApi = {
    get: jest.fn().mockResolvedValue({ ok: true, myMember: mine }),
    changeContact: jest.fn().mockResolvedValue({ ok: true, myMember: mine }),
    ...overrides,
  };
  return api;
}

async function show(api: MyMemberApi) {
  return renderAs('REGISTERED', <ContactScreen api={api} />);
}

async function share(label: string) {
  await fireEvent.press(await screen.findByRole('switch', { name: label }));
}

describe('own contact screen', () => {
  it('shows a loading state while the data arrives', async () => {
    const api = fakeApi(nothingShared, { get: jest.fn().mockReturnValue(new Promise(() => undefined)) });
    await show(api);

    expect(await screen.findByText(membersText.own.loading)).toBeOnTheScreen();
  });

  it('shows an error with a retry that loads again', async () => {
    const api = fakeApi(nothingShared, {
      get: jest
        .fn()
        .mockResolvedValueOnce({ ok: false, status: 500 })
        .mockResolvedValueOnce({ ok: true, myMember: nothingShared }),
    });
    await show(api);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.retry }));

    expect(await screen.findByText(membersText.own.row)).toBeOnTheScreen();
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('explains how to ask for the link when the user has no member', async () => {
    const api = fakeApi(nothingShared, {
      get: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'MEMBER_NOT_LINKED' }),
    });
    await show(api);

    expect(await screen.findByText(membersText.own.notLinked)).toBeOnTheScreen();
    expect(screen.queryByRole('switch')).not.toBeOnTheScreen();
  });

  it('shows the data notice, the name and the community without letting them be edited', async () => {
    await show(fakeApi());

    expect(await screen.findByText(membersText.contact.notice)).toBeOnTheScreen();
    expect(screen.getByText('Ana Pérez')).toBeOnTheScreen();
    expect(screen.getByText(communitiesText.option(twelve.number))).toBeOnTheScreen();
    expect(screen.queryByLabelText(membersText.form.givenName)).not.toBeOnTheScreen();
    expect(screen.queryByLabelText(membersText.form.surnames)).not.toBeOnTheScreen();
  });

  it('shows one share switch per field, on for what is already shared', async () => {
    await show(fakeApi({ member: ana, currentConsents: [] }));

    expect(await screen.findByRole('switch', { name: membersText.contact.share.mobile })).toBeChecked();
    expect(screen.getByRole('switch', { name: membersText.contact.share.landline })).not.toBeChecked();
    expect(screen.getByRole('switch', { name: membersText.contact.share.email })).not.toBeChecked();
    expect(screen.getByLabelText(membersText.contact.fields.mobile.label).props.value).toBe('600000001');
  });

  it('shows the format and an example before typing', async () => {
    await show(fakeApi());
    await share(membersText.contact.share.mobile);

    expect(screen.getByText(membersText.contact.fields.mobile.hint)).toBeOnTheScreen();
  });

  it('warns that hiding a field erases its value', async () => {
    await show(fakeApi());

    expect(await screen.findByText(membersText.own.hideNote)).toBeOnTheScreen();
  });

  it('saves the shared values and confirms it', async () => {
    const api = fakeApi();
    await show(api);
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '600 000 002');
    await share(membersText.contact.share.email);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.email.label), 'ana@example.org');
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));

    await waitFor(() =>
      expect(api.changeContact).toHaveBeenCalledWith({
        mobile: { shared: true, value: '600000002' },
        landline: { shared: false },
        email: { shared: true, value: 'ana@example.org' },
      }),
    );
    expect(await screen.findByText(membersText.own.saved)).toBeOnTheScreen();
    expect(screen.getByText(membersText.own.saved).props.accessibilityLiveRegion).toBe('polite');
    expect(hapticSuccess).toHaveBeenCalled();
  });

  it('sends shared false for a field the user stops sharing', async () => {
    const api = fakeApi({ member: ana, currentConsents: [] });
    await show(api);
    await share(membersText.contact.share.mobile);
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));

    await waitFor(() =>
      expect(api.changeContact).toHaveBeenCalledWith({
        mobile: { shared: false },
        landline: { shared: false },
        email: { shared: false },
      }),
    );
  });

  it('validates before sending, explains the problem and keeps what was typed', async () => {
    const api = fakeApi();
    await show(api);
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '500000005');
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));

    expect(await screen.findByText(`⚠ ${membersText.fieldErrors.mobile.PHONE_INVALID}`)).toBeOnTheScreen();
    expect(screen.getByLabelText(membersText.contact.fields.mobile.label).props.value).toBe('500000005');
    expect(api.changeContact).not.toHaveBeenCalled();
    expect(hapticFailure).toHaveBeenCalled();
  });

  it('shows the server field error and keeps what was typed', async () => {
    const api = fakeApi(nothingShared, {
      changeContact: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        fieldErrors: [{ field: 'email', code: 'EMAIL_INVALID' }],
      }),
    });
    await show(api);
    await share(membersText.contact.share.email);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.email.label), 'ana@example.org');
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));

    expect(await screen.findByText(`⚠ ${membersText.fieldErrors.email.EMAIL_INVALID}`)).toBeOnTheScreen();
    expect(screen.getByLabelText(membersText.contact.fields.email.label).props.value).toBe('ana@example.org');
  });

  it('shows a generic alert when the save fails unexpectedly', async () => {
    const api = fakeApi(nothingShared, { changeContact: jest.fn().mockRejectedValue(new Error('network down')) });
    await show(api);
    await fireEvent.press(await screen.findByRole('button', { name: membersText.contact.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.UNKNOWN}`);
  });

  it('shows who recorded a consent the moderator noted and through which channel', async () => {
    await show(fakeApi(recordedByModerator));

    expect(await screen.findByText(membersText.own.origin.title)).toBeOnTheScreen();
    expect(screen.getByText(membersText.own.origin.line('VERBAL', 'MODERATOR', 'Rosa Gómez'))).toBeOnTheScreen();
  });

  it('shows «usuario eliminado» when the user who recorded the consent no longer exists', async () => {
    await show(fakeApi(recordedByDeletedModerator));

    expect(
      await screen.findByText(membersText.own.origin.line('WRITTEN', 'MODERATOR', undefined)),
    ).toBeOnTheScreen();
    expect(screen.getByText(/usuario eliminado/)).toBeOnTheScreen();
  });

  it('does not list consents the user gave from the app', async () => {
    await show(fakeApi(recordedByMember));

    await screen.findByText(membersText.own.row);

    expect(screen.queryByText(membersText.own.origin.title)).not.toBeOnTheScreen();
  });

  it('gives every control a label, targets of 48 px and text of 18 px', async () => {
    await show(fakeApi(recordedByModerator));
    await screen.findByText(membersText.own.row);

    for (const button of screen.getAllByRole('button')) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('keeps the form usable with the font scaled to 200 percent', async () => {
    jest.spyOn(Dimensions, 'get').mockReturnValue({ width: 390, height: 844, scale: 3, fontScale: 2 });
    await show(fakeApi());

    expect(await screen.findByText(membersText.own.row)).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: membersText.contact.save })).toBeOnTheScreen();
  });
});
