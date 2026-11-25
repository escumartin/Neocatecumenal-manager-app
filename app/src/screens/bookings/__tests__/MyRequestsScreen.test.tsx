import { fireEvent, screen } from '@testing-library/react-native';
import { MyRequestsScreen } from '../my-requests';
import {
  approved,
  chapel,
  fakeBookingRequestsApi,
  fakeRoomsApi,
  hall,
  pending,
  rejected,
  renderAs,
} from '../support/test-support';
import { fontSizeOf, minHeightOf } from '../support/layout-checks';
import { bookingsText } from '@/i18n/es/bookings';

function screenWith(api = fakeBookingRequestsApi(), onRequest = jest.fn()) {
  return {
    api,
    onRequest,
    element: <MyRequestsScreen api={api} roomsApi={fakeRoomsApi()} onRequest={onRequest} />,
  };
}

describe('my requests screen', () => {
  it('lists each request with its room, date, slot, reason and status', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(hall.name)).toBeOnTheScreen();
    expect(screen.getByText(chapel.name)).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.list.when(pending))).toBeOnTheScreen();
    expect(screen.getByText(pending.reason)).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.status.PENDING)).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.status.APPROVED)).toBeOnTheScreen();
    expect(screen.getByRole('heading', { name: bookingsText.list.title })).toBeOnTheScreen();
  });

  it('shows the status and the rejection reason of a rejected request', async () => {
    const list = jest.fn().mockResolvedValue({
      ok: true,
      page: { items: [rejected, approved], page: 0, size: 100, totalItems: 2 },
    });
    const { element } = screenWith(fakeBookingRequestsApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(bookingsText.status.REJECTED)).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.rejectionReasonLine('Salón en obras'))).toBeOnTheScreen();
    expect(screen.getAllByText(/^Motivo del rechazo:/)).toHaveLength(1);
  });

  it('shows a rejected request without reason with its status only', async () => {
    const { rejectionReason: _omitted, ...withoutReason } = rejected;
    const list = jest.fn().mockResolvedValue({
      ok: true,
      page: { items: [withoutReason], page: 0, size: 100, totalItems: 1 },
    });
    const { element } = screenWith(fakeBookingRequestsApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(bookingsText.status.REJECTED)).toBeOnTheScreen();
    expect(screen.queryByText(/^Motivo del rechazo:/)).not.toBeOnTheScreen();
  });

  it('refuses a registered user without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(`⚠ ${bookingsText.forbidden}`)).toBeOnTheScreen();
    expect(api.list).not.toHaveBeenCalled();
  });

  it('shows an empty state with a way to request a room', async () => {
    const list = jest.fn().mockResolvedValue({
      ok: true,
      page: { items: [], page: 0, size: 100, totalItems: 0 },
    });
    const { element, onRequest } = screenWith(fakeBookingRequestsApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(bookingsText.list.empty)).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.list.newRequest }));
    expect(onRequest).toHaveBeenCalledTimes(1);
  });

  it('shows an error with a retry action when the list cannot be loaded', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({
        ok: true,
        page: { items: [pending], page: 0, size: 100, totalItems: 1 },
      });
    const { element } = screenWith(fakeBookingRequestsApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.list.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.list.retry }));

    expect(await screen.findByText(hall.name)).toBeOnTheScreen();
  });

  it('offers to correct the reason only on pending requests', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);
    await screen.findByText(hall.name);

    expect(screen.getAllByRole('button', { name: /^Corregir el motivo/ })).toHaveLength(1);
    expect(
      screen.getByRole('button', {
        name: bookingsText.rectify.actionFor(hall.name),
      }),
    ).toBeOnTheScreen();
    expect(
      screen.queryByRole('button', {
        name: bookingsText.rectify.actionFor(chapel.name),
      }),
    ).not.toBeOnTheScreen();
  });

  it('corrects the reason starting from the current one and shows the new one', async () => {
    const rectifyReason = jest.fn().mockResolvedValue({
      ok: true,
      request: { ...pending, reason: 'Catequesis juvenil' },
    });
    const { element } = screenWith(fakeBookingRequestsApi({ rectifyReason }));
    await renderAs('MODERATOR', element);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.rectify.actionFor(hall.name),
      }),
    );

    const field = screen.getByLabelText(bookingsText.rectify.reason);
    expect(field.props.value).toBe(pending.reason);
    expect(field.props.maxLength).toBe(500);
    expect(screen.getByText(bookingsText.reasonWarning)).toBeOnTheScreen();
    await fireEvent.changeText(field, '  Catequesis juvenil ');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.rectify.save }));

    expect(await screen.findByText('Catequesis juvenil')).toBeOnTheScreen();
    expect(rectifyReason).toHaveBeenCalledWith(pending.id, 'Catequesis juvenil');
    expect(screen.queryByLabelText(bookingsText.rectify.reason)).not.toBeOnTheScreen();
  });

  it('asks for a reason without calling the server when the field is blank', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.rectify.actionFor(hall.name),
      }),
    );

    await fireEvent.changeText(screen.getByLabelText(bookingsText.rectify.reason), '   ');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.rectify.save }));

    expect(await screen.findByText(`⚠ ${bookingsText.fieldErrors.REQUIRED}`)).toBeOnTheScreen();
    expect(api.rectifyReason).not.toHaveBeenCalled();
  });

  it('explains a request that was resolved meanwhile and keeps the editor open', async () => {
    const rectifyReason = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      code: 'BOOKING_REQUEST_ALREADY_RESOLVED',
    });
    const { element } = screenWith(fakeBookingRequestsApi({ rectifyReason }));
    await renderAs('MODERATOR', element);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.rectify.actionFor(hall.name),
      }),
    );

    await fireEvent.changeText(screen.getByLabelText(bookingsText.rectify.reason), 'Otro motivo');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.rectify.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `⚠ ${bookingsText.errors.BOOKING_REQUEST_ALREADY_RESOLVED}`,
    );
    expect(screen.getByLabelText(bookingsText.rectify.reason).props.value).toBe('Otro motivo');
  });

  it('cancels a correction without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.rectify.actionFor(hall.name),
      }),
    );

    await fireEvent.press(screen.getByRole('button', { name: bookingsText.rectify.cancel }));

    expect(screen.queryByLabelText(bookingsText.rectify.reason)).not.toBeOnTheScreen();
    expect(api.rectifyReason).not.toHaveBeenCalled();
  });

  it('names a room that is no longer listed', async () => {
    const list = jest.fn().mockResolvedValue({
      ok: true,
      page: {
        items: [{ ...approved, roomId: '6f1c1f1e-0000-4000-8000-0000000000ff' }],
        page: 0,
        size: 100,
        totalItems: 1,
      },
    });
    const { element } = screenWith(fakeBookingRequestsApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(bookingsText.list.unknownRoom)).toBeOnTheScreen();
  });

  it('gives every button a role, a label and a height of at least 48 points', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.rectify.actionFor(hall.name),
      }),
    );

    const buttons = screen.getAllByRole('button');

    expect(buttons.length).toBeGreaterThanOrEqual(2);
    for (const button of buttons) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    expect(minHeightOf(screen.getByLabelText(bookingsText.rectify.reason))).toBeGreaterThanOrEqual(48);
  });

  it('renders every text at 18 points or more', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.rectify.actionFor(hall.name),
      }),
    );

    const texts = screen.getAllByText(/\S/);

    expect(texts.length).toBeGreaterThan(8);
    for (const text of texts) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('says the room name is unavailable and offers a retry when the rooms cannot be loaded', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, rooms: [hall, chapel] });
    const api = fakeBookingRequestsApi();
    await renderAs('MODERATOR', <MyRequestsScreen api={api} roomsApi={fakeRoomsApi({ list })} />);

    expect((await screen.findAllByText(bookingsText.list.roomUnavailable)).length).toBeGreaterThan(0);
    expect(screen.queryByText(bookingsText.list.unknownRoom)).not.toBeOnTheScreen();
    expect(screen.getByText(`⚠ ${bookingsText.request.roomsError}`)).toBeOnTheScreen();

    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.retry }));

    expect(await screen.findByText(hall.name)).toBeOnTheScreen();
    expect(screen.queryByText(bookingsText.list.roomUnavailable)).not.toBeOnTheScreen();
  });

  it('does not call a room deactivated while the rooms are still loading', async () => {
    const list = jest.fn().mockReturnValue(new Promise(() => undefined));
    await renderAs('MODERATOR', <MyRequestsScreen api={fakeBookingRequestsApi()} roomsApi={fakeRoomsApi({ list })} />);

    expect((await screen.findAllByText(bookingsText.list.roomLoading)).length).toBeGreaterThan(0);
    expect(screen.queryByText(bookingsText.list.unknownRoom)).not.toBeOnTheScreen();
  });
});
