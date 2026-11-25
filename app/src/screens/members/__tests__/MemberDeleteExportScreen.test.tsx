import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Alert, Platform, Share } from 'react-native';
import { MemberDataExportScreen } from '../data-export';
import { MemberForm } from '../form';
import { ana, exportOf, fakeMembersApi, linked } from '../support/test-support';
import { fakeCommunitiesApi, renderAs } from '@/screens/communities/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { membersText } from '@/i18n/es/members';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';
import { dataExportText } from '@/lib/members/data-export-text';

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));
jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

function memberForm(api = fakeMembersApi(), memberId = ana.id) {
  const onDeleted = jest.fn();
  const onOpenExport = jest.fn();
  return {
    api,
    onDeleted,
    onOpenExport,
    element: (
      <MemberForm
        memberId={memberId}
        api={api}
        communitiesApi={fakeCommunitiesApi()}
        onSaved={jest.fn()}
        onOpenContact={jest.fn()}
        onOpenLink={jest.fn()}
        onOpenTransfer={jest.fn()}
        onOpenExport={onOpenExport}
        onDeleted={onDeleted}
      />
    ),
  };
}

function linkedApi() {
  return fakeMembersApi({ get: jest.fn().mockResolvedValue({ ok: true, member: linked }) });
}

describe('member deletion', () => {
  it('asks with a native alert that names the member and does not delete yet', async () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const { element, api } = memberForm();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));

    expect(alert).toHaveBeenCalledWith(
      membersText.delete.title('Ana Pérez'),
      membersText.delete.warning(false),
      expect.arrayContaining([expect.objectContaining({ text: membersText.delete.confirm })]),
    );
    expect(api.remove).not.toHaveBeenCalled();
  });

  it('warns that the linked user is deleted too only when the member has one', async () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const { element } = memberForm(linkedApi(), linked.id);
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));

    const warning = alert.mock.calls[0][1] ?? '';
    expect(warning).toBe(membersText.delete.warning(true));
    expect(warning).toContain('usuario');
    expect(membersText.delete.warning(false)).not.toContain('usuario');
  });

  it('deletes once the native alert is confirmed', async () => {
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const { element, api, onDeleted } = memberForm();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));
    const buttons = alert.mock.calls[0][2] ?? [];
    await act(async () => buttons.find((button) => button.text === membersText.delete.confirm)?.onPress?.());

    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1));
    expect(api.remove).toHaveBeenCalledWith(ana.id);
    expect(hapticSuccess).toHaveBeenCalledTimes(1);
  });

  it('asks with an accessible dialog on web and deletes once confirmed', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const { element, api, onDeleted } = memberForm();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));
    expect(screen.getByLabelText(membersText.delete.title('Ana Pérez')).props.role).toBe('alertdialog');
    expect(api.remove).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByRole('button', { name: membersText.delete.confirm }));

    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1));
    expect(api.remove).toHaveBeenCalledWith(ana.id);
  });

  it('keeps the member when the web dialog is cancelled', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const { element, api, onDeleted } = memberForm();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.delete.cancel }));

    expect(api.remove).not.toHaveBeenCalled();
    expect(onDeleted).not.toHaveBeenCalled();
    expect(screen.queryByLabelText(membersText.delete.title('Ana Pérez'))).not.toBeOnTheScreen();
  });

  it.each([
    ['SELF_ACTION_FORBIDDEN', 409],
    ['LAST_MODERATOR', 409],
    ['LAST_ADMINISTRATOR', 409],
    ['RESPONSIBLE_REQUIRED', 409],
    ['FORBIDDEN', 403],
  ])('explains %s and keeps the member', async (code, status) => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const remove = jest.fn().mockResolvedValue({ ok: false, status, code });
    const { element, onDeleted } = memberForm(fakeMembersApi({ remove }));
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.delete.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.errors[code]}`);
    expect(onDeleted).not.toHaveBeenCalled();
    expect(hapticFailure).toHaveBeenCalledTimes(1);
  });

  it('explains a failure of the request itself', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const remove = jest.fn().mockRejectedValue(new Error('network down'));
    const { element } = memberForm(fakeMembersApi({ remove }));
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));
    await fireEvent.press(screen.getByRole('button', { name: membersText.delete.confirm }));

    expect(await screen.findByText(`⚠ ${membersText.errors.UNKNOWN}`)).toBeOnTheScreen();
  });

  it('opens the data copy from the member form', async () => {
    const { element, onOpenExport } = memberForm();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.dataExport.row }));

    expect(onOpenExport).toHaveBeenCalledWith(ana.id);
  });

  it('keeps the dialog controls labelled and at least 48 px tall and texts at least 18 px', async () => {
    jest.replaceProperty(Platform, 'OS', 'web');
    const { element } = memberForm();
    await renderAs('MODERATOR', element);
    await fireEvent.press(await screen.findByRole('button', { name: membersText.delete.row }));

    for (const control of screen.getAllByRole('button')) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});

describe('member data copy screen', () => {
  it('shows the data readable with the author of every consent', async () => {
    const api = fakeMembersApi();
    await renderAs('MODERATOR', <MemberDataExportScreen memberId={ana.id} api={api} />);

    expect(await screen.findByText(/Copia de los datos de Ana Pérez/)).toBeOnTheScreen();
    expect(screen.getByText(/anotado por Marcos \(moderador\)/)).toBeOnTheScreen();
    expect(screen.getByText(/anotado por usuario eliminado \(administrador\)/)).toBeOnTheScreen();
    expect(screen.getByText(/anotado por el propio hermano/)).toBeOnTheScreen();
    expect(api.exportData).toHaveBeenCalledWith(ana.id);
  });

  it('shares the copy as text', async () => {
    const share = jest.spyOn(Share, 'share').mockResolvedValue({ action: 'sharedAction', activityType: undefined });
    await renderAs('MODERATOR', <MemberDataExportScreen memberId={ana.id} api={fakeMembersApi()} />);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.dataExport.share }));

    expect(share).toHaveBeenCalledWith({ message: dataExportText(exportOf(ana)), title: membersText.dataExport.title });
  });

  it('explains when the copy cannot be shared', async () => {
    jest.spyOn(Share, 'share').mockRejectedValue(new Error('unsupported'));
    await renderAs('MODERATOR', <MemberDataExportScreen memberId={ana.id} api={fakeMembersApi()} />);

    await fireEvent.press(await screen.findByRole('button', { name: membersText.dataExport.share }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.dataExport.shareError}`);
  });

  it('shows the loading state while the copy arrives', async () => {
    const api = fakeMembersApi({ exportData: jest.fn().mockReturnValue(new Promise(() => undefined)) });
    await renderAs('MODERATOR', <MemberDataExportScreen memberId={ana.id} api={api} />);

    expect(await screen.findByText(membersText.dataExport.loading)).toBeOnTheScreen();
  });

  it('shows an error with a retry when the copy cannot be loaded', async () => {
    const exportData = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, data: exportOf(ana) });
    await renderAs('MODERATOR', <MemberDataExportScreen memberId={ana.id} api={fakeMembersApi({ exportData })} />);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${membersText.dataExport.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: membersText.retry }));

    expect(await screen.findByText(/Copia de los datos de Ana Pérez/)).toBeOnTheScreen();
  });

  it('keeps the share control labelled and at least 48 px tall and texts at least 18 px', async () => {
    await renderAs('MODERATOR', <MemberDataExportScreen memberId={ana.id} api={fakeMembersApi()} />);
    await screen.findByText(/Copia de los datos de Ana Pérez/);

    for (const control of screen.getAllByRole('button')) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/./)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});
