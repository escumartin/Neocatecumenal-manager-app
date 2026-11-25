import { fireEvent, screen } from '@testing-library/react-native';
import { RequestBookingScreen } from '../request-booking';
import {
  chapel,
  communities,
  fakeBookingRequestsApi,
  fakeCommunitiesApi,
  fakeRoomsApi,
  hall,
  pending,
  renderAs,
} from '../support/test-support';
import { fontSizeOf, minHeightOf } from '../support/layout-checks';
import { bookingsText } from '@/i18n/es/bookings';
import { communitiesText } from '@/i18n/es/communities';

function screenWith(api = fakeBookingRequestsApi(), onCreated = jest.fn()) {
  return {
    api,
    onCreated,
    element: (
      <RequestBookingScreen
        api={api}
        roomsApi={fakeRoomsApi()}
        communitiesApi={fakeCommunitiesApi()}
        onCreated={onCreated}
      />
    ),
  };
}

async function fill(reason = 'Catequesis semanal') {
  await fireEvent.press(await screen.findByRole('radio', { name: hall.name }));
  await fireEvent.changeText(screen.getByLabelText(bookingsText.request.date), '2030-05-11');
  await fireEvent.changeText(screen.getByLabelText(bookingsText.request.startTime), '18:00');
  await fireEvent.changeText(screen.getByLabelText(bookingsText.request.endTime), '20:00');
  await fireEvent.changeText(screen.getByLabelText(bookingsText.request.reason), reason);
}

describe('request booking screen', () => {
  it('shows the title and the active rooms as options', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('radio', { name: hall.name })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: chapel.name })).toBeOnTheScreen();
    expect(screen.getByRole('heading', { name: bookingsText.request.title })).toBeOnTheScreen();
  });

  it('refuses a registered user without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(`⚠ ${bookingsText.forbidden}`)).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: bookingsText.request.submit })).not.toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('warns not to include personal data in the reason', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(bookingsText.reasonWarning)).toBeOnTheScreen();
  });

  it('cuts the reason at 500 characters', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect((await screen.findByLabelText(bookingsText.request.reason)).props.maxLength).toBe(500);
  });

  it('sends the request of a moderator without a community and reports it', async () => {
    const create = jest.fn().mockResolvedValue({ ok: true, request: pending });
    const { element, onCreated } = screenWith(fakeBookingRequestsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(create).toHaveBeenCalledWith({
      roomId: hall.id,
      date: '2030-05-11',
      startTime: '18:00',
      endTime: '20:00',
      reason: 'Catequesis semanal',
    });
    expect(await screen.findByRole('status')).toHaveTextContent(bookingsText.request.sent);
    expect(onCreated).toHaveBeenCalledTimes(1);
  });

  it('does not offer a community to a moderator', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await screen.findByRole('radio', { name: hall.name });
    expect(screen.queryByRole('radio', { name: communitiesText.option(1) })).not.toBeOnTheScreen();
  });

  it('asks an administrator to choose a community before sending', async () => {
    const { element, api } = screenWith();
    await renderAs('ADMINISTRATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByText(`⚠ ${bookingsText.fieldErrors.COMMUNITY_REQUIRED}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('sends the chosen community for an administrator', async () => {
    const create = jest.fn().mockResolvedValue({ ok: true, request: pending });
    const { element } = screenWith(fakeBookingRequestsApi({ create }));
    await renderAs('ADMINISTRATOR', element);

    await fill();
    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(create).toHaveBeenCalledWith(expect.objectContaining({ communityId: communities[1].id }));
  });

  it('asks for a room without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await screen.findByRole('radio', { name: hall.name });
    await fireEvent.changeText(screen.getByLabelText(bookingsText.request.reason), 'Catequesis');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByText(`⚠ ${bookingsText.fieldErrors.ROOM_REQUIRED}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('asks for a reason without calling the server when it is blank', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fill('   ');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByText(`⚠ ${bookingsText.fieldErrors.REQUIRED}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it.each(['END_NOT_AFTER_START', 'START_NOT_IN_FUTURE'])('explains the server error %s', async (code) => {
    const create = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'endTime', code }],
    });
    const { element, onCreated } = screenWith(fakeBookingRequestsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.fieldErrors[code]}`);
    expect(screen.getByLabelText(bookingsText.request.reason).props.value).toBe('Catequesis semanal');
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('explains a room that was deactivated meanwhile', async () => {
    const create = jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'ROOM_INACTIVE' });
    const { element } = screenWith(fakeBookingRequestsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.errors.ROOM_INACTIVE}`);
  });

  it('shows the occupied slots of the chosen room and day', async () => {
    const occupied = [
      {
        startTime: '18:00',
        endTime: '20:00',
        status: 'PENDING' as const,
        ownCommunity: false,
      },
    ];
    const roomsApi = fakeRoomsApi({
      occupancy: jest.fn().mockResolvedValue({
        ok: true,
        occupancy: { roomId: hall.id, date: '2030-05-11', occupied },
      }),
    });
    await renderAs(
      'MODERATOR',
      <RequestBookingScreen api={fakeBookingRequestsApi()} roomsApi={roomsApi} communitiesApi={fakeCommunitiesApi()} />,
    );

    await fill();

    expect(await screen.findByLabelText(bookingsText.occupancy.listLabel)).toBeOnTheScreen();
    expect(screen.getByRole('listitem')).toHaveAccessibleName(bookingsText.occupancy.slotLabel(occupied[0]));
    expect(roomsApi.occupancy).toHaveBeenCalledWith(hall.id, '2030-05-11', undefined);
  });

  it('does not query the occupancy before a room and a valid day are chosen', async () => {
    const roomsApi = fakeRoomsApi();
    await renderAs(
      'MODERATOR',
      <RequestBookingScreen api={fakeBookingRequestsApi()} roomsApi={roomsApi} communitiesApi={fakeCommunitiesApi()} />,
    );

    await fireEvent.press(await screen.findByRole('radio', { name: hall.name }));
    await fireEvent.changeText(screen.getByLabelText(bookingsText.request.date), '2030-05');

    expect(roomsApi.occupancy).not.toHaveBeenCalled();
  });

  it('shows the dates in conflict when the room is occupied', async () => {
    const conflicts = [{ date: '2030-05-11', startTime: '18:00', endTime: '20:00' }];
    const create = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      code: 'ROOM_OCCUPIED',
      conflicts,
    });
    const { element, onCreated } = screenWith(fakeBookingRequestsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/11\/05\/2030/);
    expect(alert).toHaveTextContent(/18:00/);
    expect(alert).toHaveTextContent(/20:00/);
    expect(screen.getByLabelText(bookingsText.request.reason).props.value).toBe('Catequesis semanal');
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('asks to update the occupancy when a race leaves no dates and reloads it', async () => {
    const create = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      code: 'ROOM_OCCUPIED',
      conflicts: [],
    });
    const roomsApi = fakeRoomsApi();
    await renderAs(
      'MODERATOR',
      <RequestBookingScreen
        api={fakeBookingRequestsApi({ create })}
        roomsApi={roomsApi}
        communitiesApi={fakeCommunitiesApi()}
      />,
    );

    await fill();
    await screen.findByText(bookingsText.occupancy.empty);
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.occupied.withoutDates}`);
    expect(roomsApi.occupancy).toHaveBeenCalledTimes(2);
  });

  it('gives every button a role, a label and a height of at least 48 points', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('radio', { name: hall.name });
    await screen.findByRole('radio', { name: communitiesText.option(1) });

    const controls = [...screen.getAllByRole('button'), ...screen.getAllByRole('radio')];

    expect(controls.length).toBeGreaterThanOrEqual(5);
    for (const control of controls) {
      expect(control.props.accessibilityLabel).toBeTruthy();
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
  });

  it('labels every field and keeps its height of at least 48 points', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);
    await screen.findByRole('radio', { name: hall.name });

    for (const label of [
      bookingsText.request.date,
      bookingsText.request.startTime,
      bookingsText.request.endTime,
      bookingsText.request.reason,
    ]) {
      expect(minHeightOf(screen.getByLabelText(label))).toBeGreaterThanOrEqual(48);
    }
  });

  it('renders every text at 18 points or more', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('radio', { name: communitiesText.option(1) });

    const texts = screen.getAllByText(/\S/);

    expect(texts.length).toBeGreaterThan(8);
    for (const text of texts) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });
});

describe('request booking screen with a weekly repetition', () => {
  async function openSeries(create = jest.fn().mockResolvedValue({ ok: true, request: pending })) {
    const { element, onCreated } = screenWith(fakeBookingRequestsApi({ create }));
    await renderAs('MODERATOR', element);
    await fill();
    await fireEvent.press(screen.getByRole('checkbox', { name: bookingsText.recurrence.toggle }));
    return { create, onCreated };
  }

  it('does not send a recurrence unless the repetition is switched on', async () => {
    const create = jest.fn().mockResolvedValue({ ok: true, request: pending });
    const { element } = screenWith(fakeBookingRequestsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(create.mock.calls[0][0]).not.toHaveProperty('recurrence');
  });

  it('sends the weekly recurrence with its end date', async () => {
    const { create, onCreated } = await openSeries();

    await fireEvent.changeText(screen.getByLabelText(bookingsText.recurrence.until), '2030-07-02');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(create).toHaveBeenCalledWith({
      roomId: hall.id,
      date: '2030-05-11',
      startTime: '18:00',
      endTime: '20:00',
      reason: 'Catequesis semanal',
      recurrence: { frequency: 'WEEKLY', until: '2030-07-02' },
    });
    expect(onCreated).toHaveBeenCalledTimes(1);
  });

  it('requires the end date without calling the server', async () => {
    const { create } = await openSeries();

    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByText(`⚠ ${bookingsText.recurrence.fieldErrors.REQUIRED}`)).toBeOnTheScreen();
    expect(create).not.toHaveBeenCalled();
  });

  it('rejects a malformed end date without calling the server', async () => {
    const { create } = await openSeries();

    await fireEvent.changeText(screen.getByLabelText(bookingsText.recurrence.until), '2/7/2030');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByText(`⚠ ${bookingsText.recurrence.fieldErrors.INVALID_FORMAT}`)).toBeOnTheScreen();
    expect(create).not.toHaveBeenCalled();
  });

  it('stops sending the recurrence when the repetition is switched off again', async () => {
    const { create } = await openSeries();
    await fireEvent.changeText(screen.getByLabelText(bookingsText.recurrence.until), '2030-07-02');

    await fireEvent.press(screen.getByRole('checkbox', { name: bookingsText.recurrence.toggle }));
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(create.mock.calls[0][0]).not.toHaveProperty('recurrence');
  });

  it.each(['RECURRENCE_TOO_LONG', 'RECURRENCE_UNTIL_BEFORE_DATE'])('explains the server error %s', async (code) => {
    const create = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'recurrence.until', code }],
    });
    await openSeries(create);

    await fireEvent.changeText(screen.getByLabelText(bookingsText.recurrence.until), '2032-07-02');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${bookingsText.recurrence.fieldErrors[code]}`);
    expect(screen.getByLabelText(bookingsText.recurrence.until).props.value).toBe('2032-07-02');
  });

  it('lists every conflicting date when the series collides and keeps the form filled', async () => {
    const create = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      code: 'ROOM_OCCUPIED',
      conflicts: [
        { date: '2030-05-25', startTime: '18:00', endTime: '20:00' },
        { date: '2030-06-08', startTime: '18:00', endTime: '20:00' },
      ],
    });
    const { onCreated } = await openSeries(create);

    await fireEvent.changeText(screen.getByLabelText(bookingsText.recurrence.until), '2030-07-02');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/25\/05\/2030/);
    expect(alert).toHaveTextContent(/08\/06\/2030/);
    expect(screen.getByLabelText(bookingsText.recurrence.until).props.value).toBe('2030-07-02');
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('keeps every control at 48 points or more and every text at 18 points or more', async () => {
    await openSeries();

    for (const control of [...screen.getAllByRole('button'), ...screen.getAllByRole('checkbox')]) {
      expect(minHeightOf(control)).toBeGreaterThanOrEqual(48);
    }
    for (const text of screen.getAllByText(/\S/)) {
      expect(fontSizeOf(text)).toBeGreaterThanOrEqual(18);
    }
  });

  it('flags only the date field when the date is malformed', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.changeText(screen.getByLabelText(bookingsText.request.date), '11/05/2030');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    const date = await screen.findByLabelText(bookingsText.request.date);
    expect(date.props['aria-invalid']).toBe(true);
    expect(date.props.accessibilityHint).toBe(bookingsText.fieldErrors.INVALID_FORMAT);
    expect(screen.getByLabelText(bookingsText.request.startTime).props['aria-invalid']).toBeFalsy();
    expect(screen.getByLabelText(bookingsText.request.endTime).props['aria-invalid']).toBeFalsy();
    expect(screen.getByLabelText(bookingsText.request.reason).props['aria-invalid']).toBeFalsy();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('flags only the reason field when the reason is blank and keeps the personal data warning in its hint', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await fill('   ');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    const reason = await screen.findByLabelText(bookingsText.request.reason);
    expect(reason.props['aria-invalid']).toBe(true);
    expect(reason.props.accessibilityHint).toContain(bookingsText.reasonWarning);
    expect(reason.props.accessibilityHint).toContain(bookingsText.fieldErrors.REQUIRED);
    expect(screen.getByLabelText(bookingsText.request.date).props['aria-invalid']).toBeFalsy();
  });

  it('announces the personal data warning on the reason field from the start', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect((await screen.findByLabelText(bookingsText.request.reason)).props.accessibilityHint).toBe(
      bookingsText.reasonWarning,
    );
  });

  it('shows the missing end date of the repetition next to its own field', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('checkbox', { name: bookingsText.recurrence.toggle }));
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    const until = await screen.findByLabelText(bookingsText.recurrence.until);
    expect(until.props['aria-invalid']).toBe(true);
    expect(until.props.accessibilityHint).toBe(bookingsText.recurrence.fieldErrors.REQUIRED);
    expect(screen.getByLabelText(bookingsText.request.date).props['aria-invalid']).toBeFalsy();
  });

  it('shows a single alert for a validation problem', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.changeText(screen.getByLabelText(bookingsText.request.startTime), 'tarde');
    await fireEvent.press(screen.getByRole('button', { name: bookingsText.request.submit }));

    expect(await screen.findAllByRole('alert')).toHaveLength(1);
  });
});
