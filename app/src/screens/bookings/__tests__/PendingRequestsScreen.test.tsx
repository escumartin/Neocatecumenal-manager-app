import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { PendingRequestsScreen } from '../pending-requests';
import {
  chapel,
  fakeBookingRequestsApi,
  fakeCommunitiesApi,
  fakeRoomsApi,
  hall,
  pending,
  pendingOfSecondCommunity,
  renderAs,
} from '../support/test-support';
import { fontSizeOf, minHeightOf } from '../support/layout-checks';
import { bookingsText } from '@/i18n/es/bookings';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';
import { hapticFailure, hapticSuccess } from '@/lib/haptics';

const pendingPage = {
  ok: true,
  page: { items: [pending, pendingOfSecondCommunity], page: 0, size: 100, totalItems: 2 },
};

function screenWith(overrides: Parameters<typeof fakeBookingRequestsApi>[0] = {}) {
  const api = fakeBookingRequestsApi({ list: jest.fn().mockResolvedValue(pendingPage), ...overrides });
  return {
    api,
    element: <PendingRequestsScreen api={api} roomsApi={fakeRoomsApi()} communitiesApi={fakeCommunitiesApi()} />,
  };
}

async function openRejection(roomName: string) {
  await fireEvent.press(await screen.findByRole('button', { name: bookingsText.resolve.rejectFor(roomName) }));
}

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));
jest.mock('@/lib/haptics', () => ({ hapticSuccess: jest.fn(), hapticFailure: jest.fn() }));

afterEach(() => {
  jest.clearAllMocks();
});

const mockedReloadOnFocus = jest.mocked(useReloadOnFocus);

async function regainFocus() {
  const reload = mockedReloadOnFocus.mock.calls.at(-1)?.[0];
  await act(async () => {
    await reload?.();
  });
}

describe('pending requests screen', () => {
  it('lists the pending requests of every community with room, community, slot and reason', async () => {
    const { element, api } = screenWith();
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByRole('heading', { name: hall.name })).toBeOnTheScreen();
    expect(screen.getByRole('heading', { name: chapel.name })).toBeOnTheScreen();
    expect(screen.queryByText(bookingsText.resolve.title)).not.toBeOnTheScreen();
    expect(screen.getByText(bookingsText.resolve.community(1))).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.resolve.community(2))).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.list.when(pending))).toBeOnTheScreen();
    expect(screen.getByText(pending.reason)).toBeOnTheScreen();
    expect(api.list).toHaveBeenCalledWith({ status: ['PENDING'], size: 100 });
  });

  it('refuses a moderator without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(`⚠ ${bookingsText.resolve.forbidden}`)).toBeOnTheScreen();
    expect(api.list).not.toHaveBeenCalled();
  });

  it('shows an empty state when nothing is pending', async () => {
    const list = jest.fn().mockResolvedValue({ ok: true, page: { items: [], page: 0, size: 100, totalItems: 0 } });
    const { element } = screenWith({ list });
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByText(bookingsText.resolve.empty)).toBeOnTheScreen();
  });

  it('shows an error with a retry action when the list cannot be loaded', async () => {
    const list = jest.fn().mockResolvedValueOnce({ ok: false, status: 500 }).mockResolvedValueOnce(pendingPage);
    const { element } = screenWith({ list });
    await renderAs('ADMINISTRATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.resolve.loadError}`);
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.resolve.retry }));

    expect(await screen.findByRole('heading', { name: hall.name })).toBeOnTheScreen();
  });

  it('approves a request and removes it from the list', async () => {
    const approve = jest.fn().mockResolvedValue({ ok: true, request: { ...pending, status: 'APPROVED' } });
    const { element } = screenWith({ approve });
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: bookingsText.resolve.approveFor(hall.name) }));

    expect(approve).toHaveBeenCalledWith(pending.id);
    expect(await screen.findByText(pendingOfSecondCommunity.reason)).toBeOnTheScreen();
    expect(screen.queryByText(pending.reason)).not.toBeOnTheScreen();
  });

  it('rejects without a reason', async () => {
    const reject = jest.fn().mockResolvedValue({ ok: true, request: { ...pending, status: 'REJECTED' } });
    const { element } = screenWith({ reject });
    await renderAs('ADMINISTRATOR', element);

    await openRejection(hall.name);
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.resolve.confirmReject }));

    expect(reject).toHaveBeenCalledWith(pending.id, undefined);
    expect(await screen.findByText(pendingOfSecondCommunity.reason)).toBeOnTheScreen();
    expect(screen.queryByText(pending.reason)).not.toBeOnTheScreen();
  });

  it('rejects with the reason trimmed', async () => {
    const reject = jest.fn().mockResolvedValue({ ok: true, request: { ...pending, status: 'REJECTED' } });
    const { element } = screenWith({ reject });
    await renderAs('ADMINISTRATOR', element);

    await openRejection(hall.name);
    await fireEvent.changeText(screen.getByLabelText(bookingsText.resolve.rejectionReason), '  Salón en obras ');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.resolve.confirmReject }));

    expect(reject).toHaveBeenCalledWith(pending.id, 'Salón en obras');
  });

  it('treats a blank rejection reason as no reason', async () => {
    const reject = jest.fn().mockResolvedValue({ ok: true, request: { ...pending, status: 'REJECTED' } });
    const { element } = screenWith({ reject });
    await renderAs('ADMINISTRATOR', element);

    await openRejection(hall.name);
    await fireEvent.changeText(screen.getByLabelText(bookingsText.resolve.rejectionReason), '    ');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.resolve.confirmReject }));

    expect(reject).toHaveBeenCalledWith(pending.id, undefined);
  });

  it('warns not to include personal data and cuts the rejection reason at 500 characters', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);

    await openRejection(hall.name);

    expect(screen.getByText(bookingsText.reasonWarning)).toBeOnTheScreen();
    expect(screen.getByLabelText(bookingsText.resolve.rejectionReason).props.maxLength).toBe(500);
  });

  it('closes the rejection form without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await openRejection(hall.name);

    await fireEvent.press(screen.getByRole('button', { name: bookingsText.resolve.cancelReject }));

    expect(screen.queryByLabelText(bookingsText.resolve.rejectionReason)).not.toBeOnTheScreen();
    expect(api.reject).not.toHaveBeenCalled();
  });

  it('explains a request resolved meanwhile and reloads the list', async () => {
    const approve = jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'BOOKING_REQUEST_ALREADY_RESOLVED' });
    const list = jest
      .fn()
      .mockResolvedValueOnce(pendingPage)
      .mockResolvedValueOnce({
        ok: true,
        page: { items: [pendingOfSecondCommunity], page: 0, size: 100, totalItems: 1 },
      });
    const { element } = screenWith({ approve, list });
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: bookingsText.resolve.approveFor(hall.name) }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.resolve.alreadyResolved}`);
    expect(list).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(pending.reason)).not.toBeOnTheScreen();
  });

  it('explains that a started request cannot be approved and keeps it to reject', async () => {
    const approve = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'startTime', code: 'START_NOT_IN_FUTURE' }],
    });
    const { element } = screenWith({ approve });
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: bookingsText.resolve.approveFor(hall.name) }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.resolve.alreadyStarted}`);
    expect(screen.getByText(pending.reason)).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: bookingsText.resolve.rejectFor(hall.name) })).toBeOnTheScreen();
  });

  it('shows the failure of a rejection and keeps the form', async () => {
    const reject = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'reason', code: 'TOO_LONG' }],
    });
    const { element } = screenWith({ reject });
    await renderAs('ADMINISTRATOR', element);

    await openRejection(hall.name);
    await fireEvent.changeText(screen.getByLabelText(bookingsText.resolve.rejectionReason), 'Motivo');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.resolve.confirmReject }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.fieldErrors.TOO_LONG}`);
    expect(screen.getByLabelText(bookingsText.resolve.rejectionReason).props.value).toBe('Motivo');
  });

  it('gives every button a role, a label and a height of at least 48 points', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await openRejection(hall.name);

    const buttons = screen.getAllByRole('button');

    expect(buttons.length).toBeGreaterThanOrEqual(4);
    for (const button of buttons) {
      expect(button.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(button)).toBeGreaterThanOrEqual(48);
    }
    expect(minHeightOf(screen.getByLabelText(bookingsText.resolve.rejectionReason))).toBeGreaterThanOrEqual(48);
  });

  it('renders every text at 18 points or more', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await openRejection(hall.name);

    const texts = screen.getAllByText(/\S/);

    expect(texts.length).toBeGreaterThan(8);
    for (const text of texts) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('says the room name is unavailable and offers a retry when the rooms cannot be loaded', async () => {
    const roomsList = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, rooms: [hall, chapel] });
    const api = fakeBookingRequestsApi({ list: jest.fn().mockResolvedValue(pendingPage) });
    await renderAs(
      'ADMINISTRATOR',
      <PendingRequestsScreen
        api={api}
        roomsApi={fakeRoomsApi({ list: roomsList })}
        communitiesApi={fakeCommunitiesApi()}
      />,
    );

    expect((await screen.findAllByText(bookingsText.list.roomUnavailable)).length).toBeGreaterThan(0);
    expect(screen.queryByText(bookingsText.list.unknownRoom)).not.toBeOnTheScreen();

    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.retry }));

    expect(await screen.findByText(hall.name)).toBeOnTheScreen();
    expect(screen.queryByText(bookingsText.list.roomUnavailable)).not.toBeOnTheScreen();
  });

  it('reloads the pending requests when the screen regains focus', async () => {
    const { element, api } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('heading', { name: hall.name });

    await regainFocus();

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('does not repeat the pending status on every card of the pending list', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('heading', { name: hall.name });

    expect(screen.queryAllByText(bookingsText.status.PENDING)).toHaveLength(0);
    expect(screen.queryAllByTestId('status-badge-glyph', { includeHiddenElements: true })).toHaveLength(0);
  });

  it('reloads the rooms too on pull to refresh', async () => {
    const roomsApi = fakeRoomsApi();
    const api = fakeBookingRequestsApi({ list: jest.fn().mockResolvedValue(pendingPage) });
    await renderAs(
      'ADMINISTRATOR',
      <PendingRequestsScreen api={api} roomsApi={roomsApi} communitiesApi={fakeCommunitiesApi()} />,
    );
    await screen.findByRole('heading', { name: hall.name });

    await act(async () => {
      await screen.getByTestId('pending-requests-list').props.refreshControl.props.onRefresh();
    });

    expect(roomsApi.list).toHaveBeenCalledTimes(2);
  });

  it('reloads the requests on pull to refresh', async () => {
    const { element, api } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('heading', { name: hall.name });

    await act(async () => {
      await screen.getByTestId('pending-requests-list').props.refreshControl.props.onRefresh();
    });

    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('gives haptic feedback when a request is approved and when approving fails', async () => {
    const approve = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, request: { ...pending, status: 'APPROVED' } });
    const { element } = screenWith({ approve });
    await renderAs('ADMINISTRATOR', element);

    await fireEvent.press(await screen.findByRole('button', { name: bookingsText.resolve.approveFor(hall.name) }));
    await waitFor(() => expect(hapticFailure).toHaveBeenCalledTimes(1));
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.resolve.approveFor(hall.name) }));

    await waitFor(() => expect(hapticSuccess).toHaveBeenCalledTimes(1));
  });
});
