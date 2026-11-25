import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { MembersList } from '../list';
import { MemberForm } from '../form';
import { MemberContactEdit } from '../contact-edit';
import { ana, fakeMembersApi, linked, luis, marta } from '../support/test-support';
import { fakeCommunitiesApi, five, renderAs, twelve } from '@/screens/communities/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { communitiesText } from '@/i18n/es/communities';
import { membersText } from '@/i18n/es/members';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));
jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
});

function list(api = fakeMembersApi(), communitiesApi = fakeCommunitiesApi()) {
  const onCreate = jest.fn();
  const onOpen = jest.fn();
  return {
    api,
    onCreate,
    onOpen,
    element: <MembersList api={api} communitiesApi={communitiesApi} onCreate={onCreate} onOpen={onOpen} />,
  };
}

function form(options: { memberId?: string; api?: ReturnType<typeof fakeMembersApi> } = {}) {
  const api = options.api ?? fakeMembersApi();
  const onSaved = jest.fn();
  const onOpenContact = jest.fn();
  return {
    api,
    onSaved,
    onOpenContact,
    element: (
      <MemberForm
        memberId={options.memberId}
        api={api}
        communitiesApi={fakeCommunitiesApi()}
        onSaved={onSaved}
        onOpenContact={onOpenContact}
      />
    ),
  };
}

function contactEdit(api = fakeMembersApi()) {
  const onSaved = jest.fn();
  return { api, onSaved, element: <MemberContactEdit memberId={ana.id} api={api} onSaved={onSaved} /> };
}

async function share(label: string) {
  await fireEvent.press(await screen.findByRole('switch', { name: label }));
}

describe('members list', () => {
  it('shows the members with their name, the consented mobile and the responsible flag', async () => {
    const { element } = list();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
    expect(screen.getByText('600000001')).toBeOnTheScreen();
    expect(screen.getByText('Luis Álvarez')).toBeOnTheScreen();
    expect(screen.getByText('Marta')).toBeOnTheScreen();
    expect(screen.getAllByText(membersText.list.responsible)).toHaveLength(1);
  });

  it('asks for the members of the own community when the user is a moderator', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);

    await screen.findByText('Ana Pérez');

    expect(api.list).toHaveBeenCalledWith(undefined);
  });

  it('shows the loading state first', async () => {
    const { element } = list(fakeMembersApi({ list: jest.fn().mockReturnValue(new Promise(() => undefined)) }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.list.loading)).toBeOnTheScreen();
  });

  it('shows an empty state when the community has no members', async () => {
    const { element } = list(fakeMembersApi({ list: jest.fn().mockResolvedValue({ ok: true, members: [] }) }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.list.empty)).toBeOnTheScreen();
  });

  it('shows an error with a retry that loads again', async () => {
    const listMembers = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, members: [ana] });
    const { element } = list(fakeMembersApi({ list: listMembers }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.list.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: membersText.retry }));

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
  });

  it('explains that the community is deactivated instead of leaving the list blank', async () => {
    const { element } = list(
      fakeMembersApi({ list: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'COMMUNITY_DEACTIVATED' }) }),
    );
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.list.deactivated)).toBeOnTheScreen();
  });

  it('opens the create form and the member file', async () => {
    const { element, onCreate, onOpen } = list();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.list.create }));
    await fireEvent.press(screen.getByRole('button', { name: /Ana Pérez/ }));

    expect(onCreate).toHaveBeenCalledWith(undefined);
    expect(onOpen).toHaveBeenCalledWith(ana.id);
  });

  it('lets an administrator choose the community before listing anything', async () => {
    const { element, api, onCreate } = list();
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(membersText.list.chooseCommunity)).toBeOnTheScreen();
    expect(api.list).not.toHaveBeenCalled();

    await fireEvent.press(await screen.findByRole('radio', { name: new RegExp(communitiesText.option(five.number)) }));

    expect(await screen.findByText('Ana Pérez')).toBeOnTheScreen();
    expect(api.list).toHaveBeenCalledWith(five.id);
    await fireEvent.press(screen.getByRole('button', { name: membersText.list.create }));
    expect(onCreate).toHaveBeenCalledWith(five.id);
  });

  it('reloads silently when the screen regains focus', async () => {
    const { element, api } = list();
    await renderAs('MODERATOR', element);
    await screen.findByText('Ana Pérez');

    const reload = jest.mocked(useReloadOnFocus).mock.calls.at(-1)?.[0];
    await act(async () => {
      await reload?.();
    });

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('gives every control a role and a label, targets of 48 px and text of 18 px', async () => {
    const { element } = list();
    await renderAs('MODERATOR', element);
    await screen.findByText('Ana Pérez');

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

describe('new member form', () => {
  it('asks for the given name without calling the server', async () => {
    const { element, api } = form();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.fieldErrors.givenName.REQUIRED}`);
    expect(api.create).not.toHaveBeenCalled();
    expect(hapticFailure).toHaveBeenCalled();
  });

  it('creates a member with only a name in the own community and sends no contact', async () => {
    const { element, api, onSaved } = form();
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), '  Ana ');
    await fireEvent.changeText(screen.getByLabelText(membersText.form.surnames), '   ');
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.create).toHaveBeenCalledWith({ communityId: twelve.id, givenName: 'Ana' });
    expect(hapticSuccess).toHaveBeenCalled();
  });

  it('limits the names to one hundred characters', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);

    expect((await screen.findByLabelText(membersText.form.givenName)).props.maxLength).toBe(100);
    expect(screen.getByLabelText(membersText.form.surnames).props.maxLength).toBe(100);
  });

  it('shows the data notice and one share switch per contact field, all off', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.contact.notice)).toBeOnTheScreen();
    for (const label of [membersText.contact.share.mobile, membersText.contact.share.landline, membersText.contact.share.email]) {
      expect(screen.getByRole('switch', { name: label })).not.toBeChecked();
    }
    expect(screen.queryByLabelText(membersText.contact.fields.mobile.label)).not.toBeOnTheScreen();
    expect(screen.queryByText(membersText.consent.title)).not.toBeOnTheScreen();
  });

  it('shows the format before typing and the right keyboard for each field', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);
    await share(membersText.contact.share.mobile);
    await share(membersText.contact.share.landline);
    await share(membersText.contact.share.email);

    expect(screen.getByText(membersText.contact.fields.mobile.hint)).toBeOnTheScreen();
    expect(screen.getByLabelText(membersText.contact.fields.mobile.label).props.keyboardType).toBe('phone-pad');
    expect(screen.getByLabelText(membersText.contact.fields.landline.label).props.keyboardType).toBe('phone-pad');
    expect(screen.getByLabelText(membersText.contact.fields.email.label).props.keyboardType).toBe('email-address');
  });

  it('asks for the consent channel and the notice once something is shared', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);

    await share(membersText.contact.share.mobile);

    expect(await screen.findByText(membersText.consent.title)).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: membersText.consent.channel.VERBAL })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: membersText.consent.channel.WRITTEN })).toBeOnTheScreen();
    expect(screen.getByRole('switch', { name: membersText.consent.noticeGiven })).toBeOnTheScreen();
  });

  it('stores nothing and explains why when no consent channel is chosen', async () => {
    const { element, api } = form();
    await renderAs('MODERATOR', element);
    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '600 000 001');
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.CONSENT_REQUIRED}`);
    expect(api.create).not.toHaveBeenCalled();
  });

  it('stores nothing and explains why when the notice is not confirmed', async () => {
    const { element, api } = form();
    await renderAs('MODERATOR', element);
    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '600 000 001');
    await fireEvent.press(screen.getByRole('radio', { name: membersText.consent.channel.VERBAL }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.NOTICE_NOT_GIVEN}`);
    expect(api.create).not.toHaveBeenCalled();
  });

  it('creates a member without a user with a mobile and verbal consent with notice', async () => {
    const { element, api, onSaved } = form();
    await renderAs('MODERATOR', element);
    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await fireEvent.changeText(screen.getByLabelText(membersText.form.surnames), 'Pérez');
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '600 000 001');
    await fireEvent.press(screen.getByRole('radio', { name: membersText.consent.channel.VERBAL }));
    await share(membersText.consent.noticeGiven);
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.create).toHaveBeenCalledWith({
      communityId: twelve.id,
      givenName: 'Ana',
      surnames: 'Pérez',
      contact: {
        mobile: { shared: true, value: '600000001' },
        landline: { shared: false },
        email: { shared: false },
      },
      onBehalfConsent: { channel: 'VERBAL', noticeGiven: true },
    });
  });

  it('rejects a malformed phone with a message showing an example and keeps what was typed', async () => {
    const { element, api } = form();
    await renderAs('MODERATOR', element);
    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '500000005');
    await fireEvent.press(screen.getByRole('radio', { name: membersText.consent.channel.WRITTEN }));
    await share(membersText.consent.noticeGiven);
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.fieldErrors.mobile.PHONE_INVALID}`);
    expect(screen.getByLabelText(membersText.contact.fields.mobile.label).props.value).toBe('500000005');
    expect(screen.getByLabelText(membersText.contact.fields.mobile.label).props.accessibilityHint).toBe(
      membersText.fieldErrors.mobile.PHONE_INVALID,
    );
    expect(api.create).not.toHaveBeenCalled();
  });

  it('rejects a malformed email and a blank shared value', async () => {
    const { element, api } = form();
    await renderAs('MODERATOR', element);
    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await share(membersText.contact.share.email);
    await share(membersText.contact.share.landline);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.email.label), 'a@b');
    await fireEvent.press(screen.getByRole('radio', { name: membersText.consent.channel.WRITTEN }));
    await share(membersText.consent.noticeGiven);
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    const alerts = await screen.findAllByRole('alert');
    expect(alerts.map((alert) => alert.props.children).flat().join(' ')).toContain(
      membersText.fieldErrors.email.EMAIL_INVALID,
    );
    expect(alerts.map((alert) => alert.props.children).flat().join(' ')).toContain(
      membersText.fieldErrors.landline.REQUIRED,
    );
    expect(api.create).not.toHaveBeenCalled();
  });

  it('turns the value into nothing when a field is no longer shared', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '600000001');

    await share(membersText.contact.share.mobile);
    await share(membersText.contact.share.mobile);

    expect(screen.getByLabelText(membersText.contact.fields.mobile.label).props.value).toBe('');
  });

  it('maps a server refusal to the field and keeps everything typed', async () => {
    const api = fakeMembersApi({
      create: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'mobile', code: 'PHONE_INVALID' }],
      }),
    });
    const { element, onSaved } = form({ api });
    await renderAs('MODERATOR', element);
    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await share(membersText.contact.share.mobile);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.mobile.label), '600000001');
    await fireEvent.press(screen.getByRole('radio', { name: membersText.consent.channel.WRITTEN }));
    await share(membersText.consent.noticeGiven);
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.fieldErrors.mobile.PHONE_INVALID}`);
    expect(screen.getByLabelText(membersText.form.givenName).props.value).toBe('Ana');
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('shows a generic message when the request fails', async () => {
    const { element } = form({ api: fakeMembersApi({ create: jest.fn().mockRejectedValue(new Error('network down')) }) });
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.UNKNOWN}`);
  });

  it('lets an administrator choose the community and refuses to continue without one', async () => {
    const { element, api } = form();
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(membersText.form.givenName), 'Ana');
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));
    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.COMMUNITY_REQUIRED}`);
    expect(api.create).not.toHaveBeenCalled();

    await fireEvent.press(await screen.findByRole('radio', { name: new RegExp(communitiesText.option(five.number)) }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.create }));

    await waitFor(() => expect(api.create).toHaveBeenCalledWith({ communityId: five.id, givenName: 'Ana' }));
  });

  it('gives every control a role and a label, targets of 48 px and text of 18 px', async () => {
    const { element } = form();
    await renderAs('MODERATOR', element);
    await share(membersText.contact.share.mobile);

    for (const control of [...screen.getAllByRole('button'), ...screen.getAllByRole('radio')]) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const control of screen.getAllByRole('switch')) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});

describe('member file', () => {
  it('prefills the names and renames the member', async () => {
    const { element, api, onSaved } = form({ memberId: ana.id });
    await renderAs('MODERATOR', element);

    expect((await screen.findByLabelText(membersText.form.givenName)).props.value).toBe('Ana');
    expect(screen.getByLabelText(membersText.form.surnames).props.value).toBe('Pérez');
    await fireEvent.changeText(screen.getByLabelText(membersText.form.givenName), 'Beatriz');
    await fireEvent.changeText(screen.getByLabelText(membersText.form.surnames), '');
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.save }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.get).toHaveBeenCalledWith(ana.id);
    expect(api.rename).toHaveBeenCalledWith(ana.id, { givenName: 'Beatriz' });
  });

  it('does not offer contact fields or consent while renaming, only a row to the contact', async () => {
    const { element, onOpenContact } = form({ memberId: ana.id });
    await renderAs('MODERATOR', element);

    await screen.findByLabelText(membersText.form.givenName);
    expect(screen.queryByRole('switch')).not.toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: membersText.form.contactRow }));

    expect(onOpenContact).toHaveBeenCalledWith(ana.id);
  });

  it('shows the loading state, then an error with a retry', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, member: ana });
    const { element } = form({ memberId: ana.id, api: fakeMembersApi({ get }) });
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.form.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: membersText.retry }));

    expect(await screen.findByLabelText(membersText.form.givenName)).toBeOnTheScreen();
  });

  it('shows the loading message while the file loads', async () => {
    const { element } = form({ memberId: ana.id, api: fakeMembersApi({ get: jest.fn().mockReturnValue(new Promise(() => undefined)) }) });
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.form.loading)).toBeOnTheScreen();
  });
});

describe('member contact', () => {
  it('prefills what the member already shares', async () => {
    const { element } = contactEdit();
    await renderAs('MODERATOR', element);

    expect((await screen.findByLabelText(membersText.contact.fields.mobile.label)).props.value).toBe('600000001');
    expect(screen.getByRole('switch', { name: membersText.contact.share.mobile })).toBeChecked();
    expect(screen.getByRole('switch', { name: membersText.contact.share.email })).not.toBeChecked();
  });

  it('rectifies a shared value without asking for consent', async () => {
    const { element, api, onSaved } = contactEdit();
    await renderAs('MODERATOR', element);

    await fireEvent.changeText(await screen.findByLabelText(membersText.contact.fields.mobile.label), '600 000 002');
    expect(screen.queryByText(membersText.consent.title)).not.toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.changeContact).toHaveBeenCalledWith(ana.id, {
      mobile: { shared: true, value: '600000002' },
      landline: { shared: false },
      email: { shared: false },
    });
  });

  it('asks for consent with notice when a field stops being shared and sends the withdrawal', async () => {
    const { element, api, onSaved } = contactEdit();
    await renderAs('MODERATOR', element);

    await share(membersText.contact.share.mobile);
    expect(await screen.findByText(membersText.consent.title)).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));
    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.CONSENT_REQUIRED}`);
    expect(api.changeContact).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByRole('radio', { name: membersText.consent.channel.WRITTEN }));
    await share(membersText.consent.noticeGiven);
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(api.changeContact).toHaveBeenCalledWith(ana.id, {
      mobile: { shared: false },
      landline: { shared: false },
      email: { shared: false },
      onBehalfConsent: { channel: 'WRITTEN', noticeGiven: true },
    });
  });

  it('maps a refused notice from the server to a message', async () => {
    const api = fakeMembersApi({
      changeContact: jest.fn().mockResolvedValue({
        ok: false,
        status: 422,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'onBehalfConsent', code: 'NOTICE_NOT_GIVEN' }],
      }),
    });
    const { element, onSaved } = contactEdit(api);
    await renderAs('MODERATOR', element);

    await share(membersText.contact.share.email);
    await fireEvent.changeText(screen.getByLabelText(membersText.contact.fields.email.label), 'ana@example.org');
    await fireEvent.press(screen.getByRole('radio', { name: membersText.consent.channel.VERBAL }));
    await share(membersText.consent.noticeGiven);
    await fireEvent.press(screen.getByRole('button', { name: membersText.contact.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors.NOTICE_NOT_GIVEN}`);
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('lets a linked member decide what is shared and only allows rectifying what is already shared', async () => {
    const { element } = contactEdit(fakeMembersApi({ get: jest.fn().mockResolvedValue({ ok: true, member: linked }) }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(membersText.contact.linkedNotice)).toBeOnTheScreen();
    for (const label of [membersText.contact.share.mobile, membersText.contact.share.landline, membersText.contact.share.email]) {
      expect(screen.getByRole('switch', { name: label })).toBeDisabled();
    }
    expect(screen.getByLabelText(membersText.contact.fields.mobile.label).props.editable).not.toBe(false);
    expect(screen.queryByText(membersText.consent.title)).not.toBeOnTheScreen();
  });

  it('shows an error with a retry when the file cannot be loaded', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 404, code: 'NOT_FOUND' })
      .mockResolvedValueOnce({ ok: true, member: luis });
    const { element } = contactEdit(fakeMembersApi({ get }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.form.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: membersText.retry }));

    expect(await screen.findByRole('switch', { name: membersText.contact.share.mobile })).toBeOnTheScreen();
  });

  it('keeps marta without a mobile as not shared', async () => {
    const { element } = contactEdit(fakeMembersApi({ get: jest.fn().mockResolvedValue({ ok: true, member: marta }) }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('switch', { name: membersText.contact.share.mobile })).not.toBeChecked();
    expect(screen.getByRole('switch', { name: membersText.contact.share.email })).toBeChecked();
  });
});
