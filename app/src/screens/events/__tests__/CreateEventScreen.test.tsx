import { fireEvent, screen } from '@testing-library/react-native';
import { CreateEventScreen } from '../create-event';
import { fakeEventsApi, wordEvent } from '../support/test-support';
import {
  chapel,
  communities,
  fakeCommunitiesApi,
  fakeRoomsApi,
  hall,
  renderAs,
} from '@/screens/bookings/support/test-support';
import { fontSizeOf, minHeightOf } from '@/screens/bookings/support/layout-checks';
import { communitiesText } from '@/i18n/es/communities';
import { eventsText } from '@/i18n/es/events';

function screenWith(api = fakeEventsApi(), onCreated = jest.fn()) {
  return {
    api,
    onCreated,
    element: (
      <CreateEventScreen api={api} roomsApi={fakeRoomsApi()} communitiesApi={fakeCommunitiesApi()} onCreated={onCreated} />
    ),
  };
}

async function fill(title = 'Palabra de apertura') {
  await fireEvent.press(await screen.findByRole('radio', { name: eventsText.types.WORD }));
  await fireEvent.press(screen.getByRole('radio', { name: hall.name }));
  await fireEvent.changeText(screen.getByLabelText(eventsText.create.eventTitle), title);
  await fireEvent.changeText(screen.getByLabelText(eventsText.create.date), '2030-05-11');
  await fireEvent.changeText(screen.getByLabelText(eventsText.create.startTime), '18:00');
  await fireEvent.changeText(screen.getByLabelText(eventsText.create.endTime), '20:00');
}

async function submit() {
  await fireEvent.press(screen.getByRole('button', { name: eventsText.create.submit }));
}

describe('create event screen', () => {
  it('shows the title, both event types and the active rooms as options', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByRole('radio', { name: eventsText.types.WORD })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: eventsText.types.CONVIVENCE })).toBeOnTheScreen();
    expect(await screen.findByRole('radio', { name: hall.name })).toBeOnTheScreen();
    expect(screen.getByRole('radio', { name: chapel.name })).toBeOnTheScreen();
    expect(screen.getByRole('heading', { name: eventsText.create.title })).toBeOnTheScreen();
  });

  it('refuses a registered user without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('REGISTERED', element);

    expect(await screen.findByText(`⚠ ${eventsText.forbidden}`)).toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: eventsText.create.submit })).not.toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('cuts the title at 120 characters', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect((await screen.findByLabelText(eventsText.create.eventTitle)).props.maxLength).toBe(120);
  });

  it('sends the event of a moderator without a community and shows the status of its request', async () => {
    const { element, onCreated, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fill();
    await submit();

    expect(api.create).toHaveBeenCalledWith({
      type: 'WORD',
      title: 'Palabra de apertura',
      roomId: hall.id,
      date: '2030-05-11',
      startTime: '18:00',
      endTime: '20:00',
    });
    expect(await screen.findByRole('status')).toHaveTextContent(eventsText.create.sent('PENDING'));
    expect(onCreated).toHaveBeenCalledWith(wordEvent);
  });

  it('sends the type of a convivence', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.press(screen.getByRole('radio', { name: eventsText.types.CONVIVENCE }));
    await submit();

    expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ type: 'CONVIVENCE' }));
  });

  it('marks the chosen type as checked', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: eventsText.types.WORD }));

    expect(screen.getByRole('radio', { name: eventsText.types.WORD })).toBeChecked();
    expect(screen.getByRole('radio', { name: eventsText.types.CONVIVENCE })).not.toBeChecked();
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
    await submit();

    expect(await screen.findByText(`⚠ ${eventsText.fieldErrors.COMMUNITY_REQUIRED}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('sends the chosen community for an administrator', async () => {
    const { element, api } = screenWith();
    await renderAs('ADMINISTRATOR', element);

    await fill();
    await fireEvent.press(await screen.findByRole('radio', { name: communitiesText.option(2) }));
    await submit();

    expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ communityId: communities[1].id }));
  });

  it('asks for the type without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: hall.name }));
    await fireEvent.changeText(screen.getByLabelText(eventsText.create.eventTitle), 'Palabra de apertura');
    await submit();

    expect(await screen.findByText(`⚠ ${eventsText.fieldErrors.TYPE_REQUIRED}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('asks for a room without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fireEvent.press(await screen.findByRole('radio', { name: eventsText.types.WORD }));
    await fireEvent.changeText(screen.getByLabelText(eventsText.create.eventTitle), 'Palabra de apertura');
    await submit();

    expect(await screen.findByText(`⚠ ${eventsText.fieldErrors.ROOM_REQUIRED}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('asks for a title without calling the server when it is blank', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fill('   ');
    await submit();

    expect(await screen.findByText(`⚠ ${eventsText.fieldErrors.TITLE_REQUIRED}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('rejects a malformed date or time without calling the server', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.changeText(screen.getByLabelText(eventsText.create.date), '11/05/2030');
    await submit();

    expect(await screen.findByText(`⚠ ${eventsText.fieldErrors.INVALID_FORMAT}`)).toBeOnTheScreen();
    expect(api.create).not.toHaveBeenCalled();
  });

  it.each(['END_NOT_AFTER_START', 'START_NOT_IN_FUTURE'])('explains the server error %s', async (code) => {
    const create = jest.fn().mockResolvedValue({
      ok: false,
      status: 422,
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field: 'endTime', code }],
    });
    const { element, onCreated } = screenWith(fakeEventsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await submit();

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${eventsText.fieldErrors[code]}`);
    expect(screen.getByLabelText(eventsText.create.eventTitle).props.value).toBe('Palabra de apertura');
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('explains a room that was deactivated meanwhile', async () => {
    const create = jest.fn().mockResolvedValue({ ok: false, status: 409, code: 'ROOM_INACTIVE' });
    const { element } = screenWith(fakeEventsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await submit();

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${eventsText.errors.ROOM_INACTIVE}`);
  });

  it('shows the dates in conflict when the room is occupied and keeps the form filled', async () => {
    const create = jest.fn().mockResolvedValue({
      ok: false,
      status: 409,
      code: 'ROOM_OCCUPIED',
      conflicts: [{ date: '2030-05-11', startTime: '18:00', endTime: '20:00' }],
    });
    const { element, onCreated } = screenWith(fakeEventsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await submit();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/11\/05\/2030/);
    expect(alert).toHaveTextContent(/18:00/);
    expect(alert).toHaveTextContent(/20:00/);
    expect(screen.getByLabelText(eventsText.create.eventTitle).props.value).toBe('Palabra de apertura');
    expect(screen.queryByRole('status')).not.toBeOnTheScreen();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('explains an unexpected failure of the call', async () => {
    const create = jest.fn().mockRejectedValue(new Error('network down'));
    const { element } = screenWith(fakeEventsApi({ create }));
    await renderAs('MODERATOR', element);

    await fill();
    await submit();

    expect(await screen.findByRole('alert')).toHaveTextContent(`⚠ ${eventsText.errors.UNKNOWN}`);
  });

  it('offers a retry when the rooms cannot be loaded', async () => {
    const list = jest
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, rooms: [hall, chapel] });
    const roomsApi = fakeRoomsApi({ list });
    await renderAs(
      'MODERATOR',
      <CreateEventScreen api={fakeEventsApi()} roomsApi={roomsApi} communitiesApi={fakeCommunitiesApi()} />,
    );

    expect(await screen.findByText(`⚠ ${eventsText.create.roomsError}`)).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole('button', { name: eventsText.create.retry }));

    expect(await screen.findByRole('radio', { name: hall.name })).toBeOnTheScreen();
  });

  it('gives every button a role, a label and a height of at least 48 points', async () => {
    const { element } = screenWith();
    await renderAs('ADMINISTRATOR', element);
    await screen.findByRole('radio', { name: hall.name });
    await screen.findByRole('radio', { name: communitiesText.option(1) });

    const controls = [...screen.getAllByRole('button'), ...screen.getAllByRole('radio')];

    expect(controls.length).toBeGreaterThanOrEqual(7);
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
      eventsText.create.eventTitle,
      eventsText.create.date,
      eventsText.create.startTime,
      eventsText.create.endTime,
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

  it('warns not to include personal data in the title', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect(await screen.findByText(eventsText.create.titleWarning)).toBeOnTheScreen();
  });

  it('announces the personal data warning on the title field', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    expect((await screen.findByLabelText(eventsText.create.eventTitle)).props.accessibilityHint).toContain(
      eventsText.create.titleWarning,
    );
  });

  it('flags only the title when it is blank', async () => {
    const { element, api } = screenWith();
    await renderAs('MODERATOR', element);

    await fill('   ');
    await submit();

    const title = await screen.findByLabelText(eventsText.create.eventTitle);
    expect(title.props['aria-invalid']).toBe(true);
    expect(title.props.accessibilityHint).toContain(eventsText.fieldErrors.TITLE_REQUIRED);
    expect(screen.getByLabelText(eventsText.create.date).props['aria-invalid']).toBeFalsy();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('flags only the start time when it is malformed', async () => {
    const { element } = screenWith();
    await renderAs('MODERATOR', element);

    await fill();
    await fireEvent.changeText(screen.getByLabelText(eventsText.create.startTime), '6pm');
    await submit();

    const start = await screen.findByLabelText(eventsText.create.startTime);
    expect(start.props['aria-invalid']).toBe(true);
    expect(start.props.accessibilityHint).toBe(eventsText.fieldErrors.INVALID_FORMAT);
    expect(screen.getByLabelText(eventsText.create.eventTitle).props['aria-invalid']).toBeFalsy();
    expect(screen.getByLabelText(eventsText.create.date).props['aria-invalid']).toBeFalsy();
    expect(screen.getByLabelText(eventsText.create.endTime).props['aria-invalid']).toBeFalsy();
    expect(await screen.findAllByRole('alert')).toHaveLength(1);
  });
});
