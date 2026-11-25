import { fireEvent, screen, waitFor } from '@testing-library/react-native';
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
import { bookingsText } from '@/i18n/es/bookings';

const started = {
  ...approved,
  id: '6f1c1f1e-0000-4000-8000-000000000045',
  date: '2020-01-10',
  reason: 'Ya empezada',
};
const series = {
  ...approved,
  id: '6f1c1f1e-0000-4000-8000-000000000046',
  occurrenceCount: 2,
  reason: 'Serie',
};

const detailOfSeries = {
  ...series,
  bookings: [
    {
      id: 'b1',
      date: '2020-01-10',
      startTime: '18:00',
      endTime: '20:00',
      status: 'APPROVED',
    },
    {
      id: 'b2',
      date: '2030-05-12',
      startTime: '18:00',
      endTime: '20:00',
      status: 'APPROVED',
    },
    {
      id: 'b3',
      date: '2030-05-19',
      startTime: '18:00',
      endTime: '20:00',
      status: 'CANCELLED',
    },
  ],
  history: [],
};

function listOf(...items: unknown[]) {
  return jest.fn().mockResolvedValue({
    ok: true,
    page: { items, page: 0, size: 100, totalItems: items.length },
  });
}

async function open(
  role: 'MODERATOR' | 'ADMINISTRATOR',
  api: ReturnType<typeof fakeBookingRequestsApi>,
) {
  await renderAs(
    role,
    <MyRequestsScreen api={api} roomsApi={fakeRoomsApi()} />,
  );
}

describe('cancelling from my requests', () => {
  it('offers to cancel only pending and approved requests that have not started', async () => {
    const api = fakeBookingRequestsApi({
      list: listOf(pending, approved, rejected, started),
    });
    await open('MODERATOR', api);
    await screen.findByText(hall.name);

    expect(
      screen.getAllByRole('button', { name: /^Cancelar la solicitud de/ }),
    ).toHaveLength(2);
    expect(
      screen.getByRole('button', {
        name: bookingsText.cancel.actionFor(hall.name),
      }),
    ).toBeOnTheScreen();
    expect(
      screen.getByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    ).toBeOnTheScreen();
  });

  it('asks for confirmation and does not call the server yet', async () => {
    const api = fakeBookingRequestsApi({ list: listOf(approved) });
    await open('MODERATOR', api);

    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    expect(
      await screen.findByLabelText(bookingsText.cancel.title(chapel.name)),
    ).toBeOnTheScreen();
    expect(api.cancel).not.toHaveBeenCalled();
  });

  it('keeps the request when the cancellation is dismissed', async () => {
    const api = fakeBookingRequestsApi({ list: listOf(approved) });
    await open('MODERATOR', api);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    await fireEvent.press(
      await screen.findByRole('button', { name: bookingsText.cancel.keep }),
    );

    expect(
      screen.queryByLabelText(bookingsText.cancel.title(chapel.name)),
    ).not.toBeOnTheScreen();
    expect(api.cancel).not.toHaveBeenCalled();
    expect(screen.getByText(bookingsText.status.APPROVED)).toBeOnTheScreen();
  });

  it('cancels after confirming and shows the request as cancelled', async () => {
    const cancel = jest.fn().mockResolvedValue({
      ok: true,
      request: { ...approved, status: 'CANCELLED' },
    });
    const api = fakeBookingRequestsApi({ list: listOf(approved), cancel });
    await open('MODERATOR', api);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    await fireEvent.press(
      await screen.findByRole('button', { name: bookingsText.cancel.confirm }),
    );

    await waitFor(() =>
      expect(screen.getByText(bookingsText.status.CANCELLED)).toBeOnTheScreen(),
    );
    expect(cancel).toHaveBeenCalledWith(approved.id);
    expect(
      screen.queryByLabelText(bookingsText.cancel.title(chapel.name)),
    ).not.toBeOnTheScreen();
    expect(
      screen.queryByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    ).not.toBeOnTheScreen();
  });

  it('lets an administrator cancel too', async () => {
    const cancel = jest.fn().mockResolvedValue({
      ok: true,
      request: { ...approved, status: 'CANCELLED' },
    });
    const api = fakeBookingRequestsApi({ list: listOf(approved), cancel });
    await open('ADMINISTRATOR', api);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    await fireEvent.press(
      await screen.findByRole('button', { name: bookingsText.cancel.confirm }),
    );

    await waitFor(() => expect(cancel).toHaveBeenCalledWith(approved.id));
  });

  it('explains that the slot already started when the server refuses and reloads the list', async () => {
    const cancel = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      code: 'BOOKING_ALREADY_STARTED',
    });
    const list = listOf(approved);
    const api = fakeBookingRequestsApi({ list, cancel });
    await open('MODERATOR', api);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    await fireEvent.press(
      await screen.findByRole('button', { name: bookingsText.cancel.confirm }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `⚠ ${bookingsText.errors.BOOKING_ALREADY_STARTED}`,
    );
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
  });

  it('explains an unexpected failure without closing the dialog', async () => {
    const cancel = jest.fn().mockRejectedValue(new Error('offline'));
    const api = fakeBookingRequestsApi({ list: listOf(approved), cancel });
    await open('MODERATOR', api);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    await fireEvent.press(
      await screen.findByRole('button', { name: bookingsText.cancel.confirm }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `⚠ ${bookingsText.errors.UNKNOWN}`,
    );
    expect(
      screen.getByLabelText(bookingsText.cancel.title(chapel.name)),
    ).toBeOnTheScreen();
  });

  it('lets the user choose between the series and a future occurrence', async () => {
    const get = jest
      .fn()
      .mockResolvedValue({ ok: true, detail: detailOfSeries });
    const cancelBooking = jest.fn().mockResolvedValue({
      ok: true,
      detail: {
        ...detailOfSeries,
        bookings: [
          detailOfSeries.bookings[0],
          { ...detailOfSeries.bookings[1], status: 'CANCELLED' },
          detailOfSeries.bookings[2],
        ],
      },
    });
    const api = fakeBookingRequestsApi({
      list: listOf(series),
      get,
      cancelBooking,
    });
    await open('MODERATOR', api);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    expect(
      await screen.findByRole('radio', {
        name: bookingsText.cancel.wholeSeries,
      }),
    ).toBeChecked();
    expect(screen.getAllByRole('radio')).toHaveLength(2);
    await fireEvent.press(
      screen.getByRole('radio', {
        name: bookingsText.cancel.oneOccurrence(detailOfSeries.bookings[1]),
      }),
    );
    await fireEvent.press(
      screen.getByRole('button', { name: bookingsText.cancel.confirm }),
    );

    await waitFor(() =>
      expect(cancelBooking).toHaveBeenCalledWith(series.id, 'b2'),
    );
    expect(get).toHaveBeenCalledWith(series.id);
    expect(screen.getByText(bookingsText.status.APPROVED)).toBeOnTheScreen();
  });

  it('cancels the whole series when that is the choice', async () => {
    const get = jest
      .fn()
      .mockResolvedValue({ ok: true, detail: detailOfSeries });
    const cancel = jest.fn().mockResolvedValue({
      ok: true,
      request: { ...series, status: 'CANCELLED' },
    });
    const api = fakeBookingRequestsApi({ list: listOf(series), get, cancel });
    await open('MODERATOR', api);
    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    await fireEvent.press(
      await screen.findByRole('button', { name: bookingsText.cancel.confirm }),
    );

    await waitFor(() => expect(cancel).toHaveBeenCalledWith(series.id));
  });

  it('reports a series whose detail cannot be loaded and does not open the dialog', async () => {
    const get = jest.fn().mockResolvedValue({ ok: false, status: 500 });
    const api = fakeBookingRequestsApi({ list: listOf(series), get });
    await open('MODERATOR', api);

    await fireEvent.press(
      await screen.findByRole('button', {
        name: bookingsText.cancel.actionFor(chapel.name),
      }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `⚠ ${bookingsText.errors.UNKNOWN}`,
    );
    expect(
      screen.queryByLabelText(bookingsText.cancel.title(chapel.name)),
    ).not.toBeOnTheScreen();
  });
});
