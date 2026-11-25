import { fireEvent, screen } from '@testing-library/react-native';
import { EventScreen } from '../event-details';
import { fakeEventsApi, wordEvent } from '../support/test-support';
import { fakeRoomsApi, hall, renderAs } from '@/screens/bookings/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { bookingsText } from '@/i18n/es/bookings';
import { eventsText } from '@/i18n/es/events';

function screenWith(api = fakeEventsApi(), roomsApi = fakeRoomsApi()) {
  return { api, element: <EventScreen eventId={wordEvent.id} api={api} roomsApi={roomsApi} /> };
}

describe('event screen', () => {
  it('shows the title, type, room, date, slot and the status of the booking request', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('heading', { name: wordEvent.title })).toBeOnTheScreen();
    expect(screen.getByText(eventsText.types.WORD)).toBeOnTheScreen();
    expect(screen.getByText(hall.name)).toBeOnTheScreen();
    expect(screen.getByText(eventsText.detail.when(wordEvent))).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.status.PENDING)).toBeOnTheScreen();
  });

  it.each(['APPROVED', 'REJECTED', 'CANCELLED'] as const)('shows the current status %s', async (bookingStatus) => {
    const get = jest.fn().mockResolvedValue({ ok: true, event: { ...wordEvent, bookingStatus } });
    const { element } = screenWith(fakeEventsApi({ get }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(bookingsText.status[bookingStatus])).toBeOnTheScreen();
  });

  it('says so when the room is no longer listed', async () => {
    const get = jest.fn().mockResolvedValue({
      ok: true,
      event: { ...wordEvent, roomId: '6f1c1f1e-0000-4000-8000-000000000099' },
    });
    const { element } = screenWith(fakeEventsApi({ get }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(eventsText.detail.unknownRoom)).toBeOnTheScreen();
  });

  it('shows an unknown event as not found', async () => {
    const get = jest.fn().mockResolvedValue({ ok: false, status: 404, code: 'NOT_FOUND' });
    const { element } = screenWith(fakeEventsApi({ get }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${eventsText.detail.notFound}`);
  });

  it('shows an event of another community as forbidden', async () => {
    const get = jest.fn().mockResolvedValue({ ok: false, status: 403, code: 'FORBIDDEN' });
    const { element } = screenWith(fakeEventsApi({ get }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${eventsText.detail.forbidden}`);
  });

  it('refuses a registered user without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(`⚠ ${eventsText.detail.forbidden}`)).toBeOnTheScreen();
    expect(api.get).not.toHaveBeenCalled();
  });

  it('offers a retry after a load error', async () => {
    const get = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, event: wordEvent });
    const { element } = screenWith(fakeEventsApi({ get }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(`⚠ ${eventsText.detail.loadError}`)).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: eventsText.detail.retry }));

    expect(await screen.findByRole('heading', { name: wordEvent.title })).toBeOnTheScreen();
  });

  it('keeps the retry button at 48 points and every text at 18 points or more', async () => {
    const get = jest.fn().mockResolvedValue({ ok: false, status: 500 });
    const { element } = screenWith(fakeEventsApi({ get }));
    await renderAs('MODERATOR', element);
    await screen.findByText(`⚠ ${eventsText.detail.loadError}`);

    const retry = screen.getByRole('button', { name: eventsText.detail.retry });

    expect(retry.props.accessibilityLabel).toBeTruthy();
    expect(minHeightOf(retry)).toBeGreaterThanOrEqual(48);
    for (const text of screen.getAllByText(/\S/)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('renders every text of an event at 18 points or more', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);
    await screen.findByRole('heading', { name: wordEvent.title });

    const texts = screen.getAllByText(/\S/);

    expect(texts.length).toBeGreaterThan(5);
    for (const text of texts) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('does not claim the room is deactivated while the rooms are loading', async () => {
    const list = jest.fn().mockReturnValue(new Promise(() => undefined));
    const { element } = screenWith(fakeEventsApi(), fakeRoomsApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(eventsText.detail.roomLoading)).toBeOnTheScreen();
    expect(screen.queryByText(eventsText.detail.unknownRoom)).not.toBeOnTheScreen();
  });

  it('says the room name is unavailable when the rooms cannot be loaded', async () => {
    const list = jest.fn().mockResolvedValue({ ok: false, status: 500 });
    const { element } = screenWith(fakeEventsApi(), fakeRoomsApi({ list }));
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(eventsText.detail.roomUnavailable)).toBeOnTheScreen();
    expect(screen.queryByText(eventsText.detail.unknownRoom)).not.toBeOnTheScreen();
  });
});
