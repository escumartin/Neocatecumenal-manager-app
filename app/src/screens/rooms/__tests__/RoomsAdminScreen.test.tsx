import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { Alert, Platform } from 'react-native';
import { RoomsAdminScreen } from '../rooms-admin';
import { chapel, fakeRoomsApi, hall, renderAs } from '../support/test-support';
import { roomsText } from '@/i18n/es/rooms';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

const tailwindConfig = jest.requireActual('../../../../tailwind.config.js');

function pixels(value: string): number {
  return Number.parseInt(value, 10);
}

function classesOf(element: { props: { className?: unknown } }): string[] {
  return String(element.props.className ?? '').split(/\s+/);
}

function fontSizeOf(element: { props: { className?: unknown } }): number {
  const token = classesOf(element)
    .map((name) => name.replace(/^text-/, ''))
    .filter((name) => name in tailwindConfig.theme.extend.fontSize)
    .pop();
  return token ? pixels(tailwindConfig.theme.extend.fontSize[token][0]) : 0;
}

function minHeightOf(element: { props: { className?: unknown } }): number {
  const step = classesOf(element)
    .find((name) => name.startsWith('min-h-'))
    ?.replace('min-h-', '');
  return step ? pixels(tailwindConfig.theme.extend.spacing[step]) : 0;
}

afterEach(() => {
  jest.clearAllMocks();
  jest.restoreAllMocks();
});

function row(name: string) {
  return screen.findByRole('button', { name: new RegExp(name) });
}

async function startDeactivation(name: string) {
  await fireEvent(await row(name), 'longPress');
}

function useWebDialog() {
  jest.replaceProperty(Platform, 'OS', 'web');
}

function nativeAlert() {
  return jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
}

describe('rooms admin screen', () => {
  it('lists the active rooms for an administrator', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    expect(await screen.findByText(hall.name)).toBeOnTheScreen();
    expect(screen.getByText(chapel.name)).toBeOnTheScreen();
    expect(screen.queryByText(roomsText.title)).not.toBeOnTheScreen();
  });

  it.each(['MODERATOR', 'REGISTERED'] as const)('refuses %s without calling the server', async (role) => {
    const api = fakeRoomsApi();
    await renderAs(role, <RoomsAdminScreen api={api} />);

    expect(await screen.findByText(`⚠ ${roomsText.forbidden}`)).toBeOnTheScreen();
    expect(screen.queryByText(hall.name)).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: roomsText.add.submit })).not.toBeOnTheScreen();
    expect(api.list).not.toHaveBeenCalled();
  });

  it('shows an empty state when there are no rooms', async () => {
    const api = fakeRoomsApi({ list: jest.fn().mockResolvedValue({ ok: true, rooms: [] }) });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    expect(await screen.findByText(roomsText.empty)).toBeOnTheScreen();
  });

  it('shows an error with a retry action when the list cannot be loaded', async () => {
    const api = fakeRoomsApi({
      list: jest
        .fn()
        .mockResolvedValueOnce({ ok: false, status: 500 })
        .mockResolvedValueOnce({ ok: true, rooms: [hall] }),
    });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${roomsText.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: roomsText.retry }));

    expect(await screen.findByText(hall.name)).toBeOnTheScreen();
  });

  it('adds a room with the trimmed name and clears the field', async () => {
    const created = { id: '6f1c1f1e-0000-4000-8000-000000000033', name: 'Aula', active: true };
    const api = fakeRoomsApi({ create: jest.fn().mockResolvedValue({ ok: true, room: created }) });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    await fireEvent.changeText(await screen.findByLabelText(roomsText.add.name), '  Aula ');
    await fireEvent.press(screen.getByRole('button', { name: roomsText.add.submit }));

    expect(await screen.findByText('Aula')).toBeOnTheScreen();
    expect(api.create).toHaveBeenCalledWith('Aula');
    expect(screen.getByLabelText(roomsText.add.name).props.value).toBe('');
  });

  it('asks for a name without calling the server when the field is blank', async () => {
    const api = fakeRoomsApi();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    await fireEvent.changeText(await screen.findByLabelText(roomsText.add.name), '   ');
    await fireEvent.press(screen.getByRole('button', { name: roomsText.add.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${roomsText.fieldErrors.REQUIRED}`);
    expect(api.create).not.toHaveBeenCalled();
  });

  it('explains a repeated name and keeps what was typed', async () => {
    const api = fakeRoomsApi({
      create: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'ROOM_NAME_TAKEN' }),
    });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    await fireEvent.changeText(await screen.findByLabelText(roomsText.add.name), 'Capilla');
    await fireEvent.press(screen.getByRole('button', { name: roomsText.add.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${roomsText.errors.ROOM_NAME_TAKEN}`);
    expect(screen.getByLabelText(roomsText.add.name).props.value).toBe('Capilla');
  });

  it('renames a room starting from its current name', async () => {
    const renamed = { ...hall, name: 'Salón pequeño' };
    const api = fakeRoomsApi({ rename: jest.fn().mockResolvedValue({ ok: true, room: renamed }) });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    await fireEvent.press(await row(hall.name));
    const field = screen.getByLabelText(roomsText.rename.name);
    expect(field.props.value).toBe(hall.name);
    await fireEvent.changeText(field, ' Salón pequeño ');
    await fireEvent.press(screen.getByRole('button', { name: roomsText.rename.save }));

    expect(await screen.findByText('Salón pequeño')).toBeOnTheScreen();
    expect(api.rename).toHaveBeenCalledWith(hall.id, 'Salón pequeño');
    expect(screen.queryByLabelText(roomsText.rename.name)).not.toBeOnTheScreen();
  });

  it('cancels a rename without calling the server', async () => {
    const api = fakeRoomsApi();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await fireEvent.press(await row(hall.name));

    await fireEvent.press(screen.getByRole('button', { name: roomsText.rename.cancel }));

    expect(screen.queryByLabelText(roomsText.rename.name)).not.toBeOnTheScreen();
    expect(screen.getByText(hall.name)).toBeOnTheScreen();
    expect(api.rename).not.toHaveBeenCalled();
  });

  it('explains a rename to a name already taken and keeps the editor open', async () => {
    const api = fakeRoomsApi({
      rename: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'ROOM_NAME_TAKEN' }),
    });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await fireEvent.press(await row(hall.name));

    await fireEvent.changeText(screen.getByLabelText(roomsText.rename.name), 'Capilla');
    await fireEvent.press(screen.getByRole('button', { name: roomsText.rename.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${roomsText.errors.ROOM_NAME_TAKEN}`);
    expect(screen.getByLabelText(roomsText.rename.name)).toBeOnTheScreen();
  });

  it('hints that a room row can be held to deactivate it', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    expect(roomsText.deactivateHint).toEqual(expect.any(String));
    expect((await row(hall.name)).props.accessibilityHint).toBe(roomsText.deactivateHint);
  });

  it('asks for confirmation before deactivating and does not call the server yet', async () => {
    useWebDialog();
    const api = fakeRoomsApi();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    await startDeactivation(hall.name);

    expect(screen.getByLabelText(roomsText.deactivate.title(hall.name))).toBeOnTheScreen();
    expect(screen.getByText(roomsText.deactivate.warning)).toBeOnTheScreen();
    expect(api.deactivate).not.toHaveBeenCalled();
  });

  it('presents the confirmation as an alert dialog that says the deactivation is permanent', async () => {
    useWebDialog();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    await startDeactivation(hall.name);

    expect(screen.getByLabelText(roomsText.deactivate.title(hall.name)).props.role).toBe('alertdialog');
    expect(roomsText.deactivate.warning).toContain('definitiva');
    expect(screen.getByRole('button', { name: roomsText.deactivate.confirm })).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: roomsText.deactivate.cancel })).toBeOnTheScreen();
  });

  it('keeps the room when the deactivation is cancelled', async () => {
    useWebDialog();
    const api = fakeRoomsApi();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await startDeactivation(hall.name);

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.cancel }));

    expect(screen.queryByLabelText(roomsText.deactivate.title(hall.name))).not.toBeOnTheScreen();
    expect(screen.getByText(hall.name)).toBeOnTheScreen();
    expect(api.deactivate).not.toHaveBeenCalled();
  });

  it('removes the room from the list once the deactivation is confirmed', async () => {
    useWebDialog();
    const api = fakeRoomsApi({ deactivate: jest.fn().mockResolvedValue({ ok: true }) });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await startDeactivation(hall.name);

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.confirm }));

    await waitFor(() => expect(screen.queryByText(hall.name)).not.toBeOnTheScreen());
    expect(api.deactivate).toHaveBeenCalledWith(hall.id);
    expect(screen.queryByLabelText(roomsText.deactivate.title(hall.name))).not.toBeOnTheScreen();
    expect(screen.getByText(chapel.name)).toBeOnTheScreen();
  });

  it('tells how many bookings keep the room from being deactivated', async () => {
    useWebDialog();
    const api = fakeRoomsApi({
      deactivate: jest
        .fn()
        .mockResolvedValue({ ok: false, status: 409, code: 'ROOM_HAS_ACTIVE_BOOKINGS', activeBookings: 2 }),
    });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await startDeactivation(hall.name);

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${roomsText.activeBookings(2)}`);
    expect(screen.getByText(hall.name)).toBeOnTheScreen();
  });

  it('explains a room that was already deactivated', async () => {
    useWebDialog();
    const api = fakeRoomsApi({
      deactivate: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'ROOM_INACTIVE' }),
    });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await startDeactivation(hall.name);

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.confirm }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${roomsText.errors.ROOM_INACTIVE}`);
  });

  it('gives every button a role, a label and a height of at least 48 points', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);
    await screen.findByText(hall.name);
    await fireEvent.press(await row(chapel.name));

    const buttons = screen.getAllByRole('button');

    expect(buttons.length).toBeGreaterThanOrEqual(4);
    for (const button of buttons) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
  });

  it('labels every field and keeps its height of at least 48 points', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);
    await screen.findByText(hall.name);
    await fireEvent.press(await row(hall.name));

    for (const label of [roomsText.add.name, roomsText.rename.name]) {
      expect(minHeightOf(screen.getByLabelText(label))).toBeGreaterThanOrEqual(48);
    }
  });

  it('renders every text at 18 points or more', async () => {
    useWebDialog();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);
    await screen.findByText(hall.name);
    await startDeactivation(hall.name);

    const texts = screen.getAllByText(/\S/);

    expect(texts.length).toBeGreaterThan(8);
    for (const text of texts) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('closes the confirmation and reports a failure when the deactivation request throws', async () => {
    useWebDialog();
    const api = fakeRoomsApi({ deactivate: jest.fn().mockRejectedValue(new Error('network down')) });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await startDeactivation(hall.name);

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.confirm }));

    expect(await screen.findByText(`⚠ ${roomsText.errors.UNKNOWN}`)).toBeOnTheScreen();
    expect(screen.queryByLabelText(roomsText.deactivate.title(hall.name))).not.toBeOnTheScreen();
    expect(screen.getByText(hall.name)).toBeOnTheScreen();
  });

  it('has no inline rename or deactivate buttons on the rows', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    await screen.findByText(hall.name);

    expect(screen.queryByRole('button', { name: roomsText.rename.actionFor(hall.name) })).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: roomsText.deactivate.actionFor(hall.name) })).not.toBeOnTheScreen();
  });

  it('tells how to deactivate a room in the footer of the list', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    expect(await screen.findByText(roomsText.deactivateTip)).toBeOnTheScreen();
  });

  it('offers deactivation as a destructive row inside the rename editor', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    await fireEvent.press(await row(hall.name));

    expect(screen.getByRole('button', { name: roomsText.deactivate.action })).toBeOnTheScreen();
    expect(screen.getByText(roomsText.deactivate.action).props.className).toContain('text-destructive');
  });

  it('does not offer the deactivate row while no room is being edited', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    await screen.findByText(hall.name);

    expect(screen.queryByRole('button', { name: roomsText.deactivate.action })).not.toBeOnTheScreen();
  });

  it('deactivates from the editor after confirming in the dialog', async () => {
    useWebDialog();
    const api = fakeRoomsApi({ deactivate: jest.fn().mockResolvedValue({ ok: true }) });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await fireEvent.press(await row(hall.name));

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.action }));
    expect(screen.getByLabelText(roomsText.deactivate.title(hall.name))).toBeOnTheScreen();
    expect(api.deactivate).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.confirm }));

    await waitFor(() => expect(screen.queryByText(hall.name)).not.toBeOnTheScreen());
    expect(api.deactivate).toHaveBeenCalledWith(hall.id);
    expect(hapticSuccess).toHaveBeenCalledTimes(1);
  });

  it('asks with a native alert when deactivating from the editor', async () => {
    const alert = nativeAlert();
    const api = fakeRoomsApi();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await fireEvent.press(await row(hall.name));

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.action }));

    expect(alert).toHaveBeenCalledWith(
      roomsText.deactivate.title(hall.name),
      roomsText.deactivate.warning,
      expect.arrayContaining([expect.objectContaining({ style: 'destructive' })]),
    );
    expect(api.deactivate).not.toHaveBeenCalled();
  });

  it('explains a refused deactivation from the editor', async () => {
    useWebDialog();
    const api = fakeRoomsApi({
      deactivate: jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'ROOM_HAS_ACTIVE_BOOKINGS', activeBookings: 2 }),
    });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await fireEvent.press(await row(hall.name));

    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.action }));
    await fireEvent.press(screen.getByRole('button', { name: roomsText.deactivate.confirm }));

    expect(await screen.findByRole('alert')).toBeOnTheScreen();
    expect(hapticFailure).toHaveBeenCalled();
  });

  it('mentions both ways to deactivate in the footer', async () => {
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    const tip = await screen.findByText(roomsText.deactivateTip);

    expect(tip).toBeOnTheScreen();
    expect(roomsText.deactivateTip).toContain(roomsText.deactivate.action);
    expect(roomsText.deactivateTip).toContain('mantén pulsado');
  });

  it('asks with a native alert before deactivating and does not call the server yet', async () => {
    const alert = nativeAlert();
    const api = fakeRoomsApi();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);

    await startDeactivation(hall.name);

    expect(alert).toHaveBeenCalledWith(
      roomsText.deactivate.title(hall.name),
      roomsText.deactivate.warning,
      expect.arrayContaining([expect.objectContaining({ text: roomsText.deactivate.confirm, style: 'destructive' })]),
    );
    expect(api.deactivate).not.toHaveBeenCalled();
  });

  it('deactivates the room once the native alert is confirmed', async () => {
    const alert = nativeAlert();
    const api = fakeRoomsApi({ deactivate: jest.fn().mockResolvedValue({ ok: true }) });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await startDeactivation(hall.name);

    await act(async () => {
      alert.mock.calls[0][2]?.find((button) => button.style === 'destructive')?.onPress?.();
    });

    await waitFor(() => expect(screen.queryByText(hall.name)).not.toBeOnTheScreen());
    expect(api.deactivate).toHaveBeenCalledWith(hall.id);
  });

  it('offers deactivation as an accessibility action of the row', async () => {
    const alert = nativeAlert();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={fakeRoomsApi()} />);

    const target = await row(hall.name);
    await fireEvent(target, 'accessibilityAction', { nativeEvent: { actionName: 'deactivate' } });

    expect(alert).toHaveBeenCalledTimes(1);
    expect(target.props.accessibilityActions).toEqual([
      { name: 'deactivate', label: roomsText.deactivate.actionFor(hall.name) },
    ]);
  });

  it('reloads the rooms on pull to refresh', async () => {
    const api = fakeRoomsApi();
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await screen.findByText(hall.name);

    await act(async () => {
      await screen.getByTestId('rooms-list').props.refreshControl.props.onRefresh();
    });

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('gives haptic feedback when a room is added and when adding fails', async () => {
    const created = { id: '6f1c1f1e-0000-4000-8000-000000000033', name: 'Aula', active: true };
    const api = fakeRoomsApi({
      create: jest
        .fn()
        .mockResolvedValueOnce({ ok: true, room: created })
        .mockResolvedValueOnce({ ok: false, status: 409, code: 'ROOM_NAME_TAKEN' }),
    });
    await renderAs('ADMINISTRATOR', <RoomsAdminScreen api={api} />);
    await fireEvent.changeText(await screen.findByLabelText(roomsText.add.name), 'Aula');

    await fireEvent.press(screen.getByRole('button', { name: roomsText.add.submit }));
    await waitFor(() => expect(hapticSuccess).toHaveBeenCalledTimes(1));
    await fireEvent.changeText(screen.getByLabelText(roomsText.add.name), 'Capilla');
    await fireEvent.press(screen.getByRole('button', { name: roomsText.add.submit }));

    await waitFor(() => expect(hapticFailure).toHaveBeenCalledTimes(1));
  });
});
