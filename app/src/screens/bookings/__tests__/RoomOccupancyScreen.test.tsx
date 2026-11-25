import { act, fireEvent, screen, waitFor } from '@testing-library/react-native';
import { RoomOccupancyScreen } from '@/screens/rooms/room-occupancy';
import {
  chapel,
  communities,
  emptyOccupancy,
  fakeCommunitiesApi,
  fakeRoomsApi,
  hall,
  renderAs,
} from '../support/test-support';
import { fontSizeOf, minHeightOf } from '../support/layout-checks';
import { bookingsText } from '@/i18n/es/bookings';
import { communitiesText } from '@/i18n/es/communities';
import { useReloadOnFocus } from '@/hooks/navigation/use-reload-on-focus';

const occupied = [
  {
    startTime: '09:00',
    endTime: '10:00',
    status: 'APPROVED' as const,
    ownCommunity: false,
  },
  {
    startTime: '18:00',
    endTime: '20:00',
    status: 'PENDING' as const,
    ownCommunity: true,
  },
];

function screenWith(roomsApi = fakeRoomsApi()) {
  return {
    roomsApi,
    element: <RoomOccupancyScreen roomsApi={roomsApi} communitiesApi={fakeCommunitiesApi()} />,
  };
}

async function chooseRoomAndDate(room = hall.name, date = '2030-05-11') {
  await fireEvent.press(await screen.findByRole('radio', { name: room }));
  await fireEvent.changeText(screen.getByLabelText(bookingsText.occupancy.date), date);
}

jest.mock('@/hooks/navigation/use-reload-on-focus', () => ({ useReloadOnFocus: jest.fn() }));

const mockedReloadOnFocus = jest.mocked(useReloadOnFocus);

async function regainFocus() {
  const reload = mockedReloadOnFocus.mock.calls.at(-1)?.[0];
  await act(async () => {
    await reload?.();
  });
}

describe('room occupancy screen', () => {
  it('shows the active rooms as options without repeating the screen title', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('radio', { name: hall.name })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: chapel.name })).toBeOnTheScreen();
    expect(screen.queryByText(bookingsText.occupancy.title)).not.toBeOnTheScreen();
  });

  it('refuses a registered user without calling the server', async () => {
    const { element, roomsApi } = screenWith();
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(`⚠ ${bookingsText.forbidden}`)).toBeOnTheScreen();
    expect(roomsApi.list).not.toHaveBeenCalled();
    expect(roomsApi.occupancy).not.toHaveBeenCalled();
  });

  it('asks for a room and a day before querying', async () => {
    const { element, roomsApi } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(bookingsText.occupancy.choose)).toBeOnTheScreen();
    expect(roomsApi.occupancy).not.toHaveBeenCalled();
  });

  it('lists the occupied slots with a role, a label and the status written in text', async () => {
    const occupancy = jest.fn().mockResolvedValue({
      ok: true,
      occupancy: { ...emptyOccupancy, occupied },
    });
    const { element } = screenWith(fakeRoomsApi({ occupancy }));
    await renderAs('MODERATOR', element);

    await chooseRoomAndDate();

    expect(await screen.findByLabelText(bookingsText.occupancy.listLabel)).toBeOnTheScreen();
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveAccessibleName(bookingsText.occupancy.slotLabel(occupied[0]));
    expect(items[1]).toHaveAccessibleName(bookingsText.occupancy.slotLabel(occupied[1]));
    expect(screen.getByText(bookingsText.status.APPROVED)).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.occupancy.ownCommunity)).toBeOnTheScreen();
    expect(screen.getByText(bookingsText.occupancy.otherCommunity)).toBeOnTheScreen();
    expect(occupancy).toHaveBeenCalledWith(hall.id, '2030-05-11', undefined);
  });

  it('says so when the day has no occupied slots', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await chooseRoomAndDate();

    expect(await screen.findByText(bookingsText.occupancy.empty)).toBeOnTheScreen();
  });

  it('does not query with a malformed date', async () => {
    const { element, roomsApi } = screenWith();
    await renderAs('MODERATOR', element);

    await chooseRoomAndDate(hall.name, '11/05/2030');

    expect(roomsApi.occupancy).not.toHaveBeenCalled();
  });

  it('does not offer a community to a moderator', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await screen.findByRole('radio', { name: hall.name });
    expect(screen.queryByRole('radio', { name: communitiesText.option(1) })).not.toBeOnTheScreen();
  });

  it('makes an administrator choose a community before querying', async () => {
    const { element, roomsApi } = screenWith();
    await renderAs('ADMINISTRATOR', element);

    await chooseRoomAndDate();

    expect(await screen.findByText(bookingsText.occupancy.chooseCommunity)).toBeOnTheScreen();
    expect(roomsApi.occupancy).not.toHaveBeenCalled();
  });

  it('queries with the chosen community for an administrator', async () => {
    const { element, roomsApi } = screenWith();
    await renderAs('ADMINISTRATOR', element);

    await chooseRoomAndDate();
    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));

    expect(await screen.findByText(bookingsText.occupancy.empty)).toBeOnTheScreen();
    expect(roomsApi.occupancy).toHaveBeenCalledWith(hall.id, '2030-05-11', communities[1].id);
  });

  it('offers to retry when the occupancy cannot be loaded', async () => {
    const occupancy = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({
        ok: true,
        occupancy: { ...emptyOccupancy, occupied },
      });
    const { element } = screenWith(fakeRoomsApi({ occupancy }));
    await renderAs('MODERATOR', element);

    await chooseRoomAndDate();
    expect(await screen.findByText(`⚠ ${bookingsText.occupancy.loadError}`)).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.occupancy.retry }));

    expect(await screen.findAllByRole('listitem')).toHaveLength(2);
  });

  it('gives every control a role, a label and a height of at least 48 points', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('radio', { name: communitiesText.option(1) });

    const controls = [...screen.getAllByRole('radio')];

    expect(controls.length).toBeGreaterThanOrEqual(4);
    for (const control of controls) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    expect(minHeightOf(screen.getByLabelText(bookingsText.occupancy.date))).toBeGreaterThanOrEqual(48);
  });

  it('writes the slots in at least 18 points', async () => {
    const occupancy = jest.fn().mockResolvedValue({
      ok: true,
      occupancy: { ...emptyOccupancy, occupied },
    });
    const { element } = screenWith(fakeRoomsApi({ occupancy }));
    await renderAs('MODERATOR', element);

    await chooseRoomAndDate();
    await screen.findAllByRole('listitem');

    for (const text of [
      screen.getByText(bookingsText.status.APPROVED),
      screen.getByText(bookingsText.occupancy.ownCommunity),
      screen.getByText(bookingsText.occupancy.slotText(occupied[0])),
    ]) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('reloads the occupancy when the screen regains focus', async () => {
    const { element, roomsApi } = screenWith();
    await renderAs('MODERATOR', element);
    await chooseRoomAndDate();
    await waitFor(() => expect(roomsApi.occupancy).toHaveBeenCalledTimes(1));

    await regainFocus();

    expect(roomsApi.occupancy).toHaveBeenCalledTimes(2);
  });

  it('marks the chosen room as selected and the others as not', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: hall.name }));

    expect(screen.getByRole('radio', { name: hall.name })).toBeChecked();
    expect(screen.getByRole('radio', { name: chapel.name })).not.toBeChecked();
  });

  it('offers the rooms as one grouped list', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);
    await screen.findByRole('radio', { name: hall.name });

    expect(screen.getAllByTestId('list-section-card')).toHaveLength(1);
  });

  it('marks every occupied slot status with a glyph next to its text', async () => {
    const occupancy = jest.fn().mockResolvedValue({ ok: true, occupancy: { ...emptyOccupancy, occupied } });
    const { element } = screenWith(fakeRoomsApi({ occupancy }));
    await renderAs('MODERATOR', element);

    await chooseRoomAndDate();
    await screen.findAllByRole('listitem');

    expect(screen.getAllByTestId('status-badge-glyph', { includeHiddenElements: true })).toHaveLength(2);
  });

  it('reloads the occupancy on pull to refresh', async () => {
    const { element, roomsApi } = screenWith();
    await renderAs('MODERATOR', element);
    await chooseRoomAndDate();
    await waitFor(() => expect(roomsApi.occupancy).toHaveBeenCalledTimes(1));

    await act(async () => {
      await screen.getByTestId('occupancy-scroll').props.refreshControl.props.onRefresh();
    });

    expect(roomsApi.occupancy).toHaveBeenCalledTimes(2);
  });
});
