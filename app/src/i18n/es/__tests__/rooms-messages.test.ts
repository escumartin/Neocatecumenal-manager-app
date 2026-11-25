import { messageForRoomsFailure, roomsText } from '../rooms';

describe('messageForRoomsFailure', () => {
  it('explains a repeated name', () => {
    expect(messageForRoomsFailure({ code: 'ROOM_NAME_TAKEN' })).toBe(
      roomsText.errors.ROOM_NAME_TAKEN,
    );
  });

  it('explains a room that is already deactivated', () => {
    expect(messageForRoomsFailure({ code: 'ROOM_INACTIVE' })).toBe(
      roomsText.errors.ROOM_INACTIVE,
    );
  });

  it('tells how many bookings keep the room from being deactivated', () => {
    const message = messageForRoomsFailure({
      code: 'ROOM_HAS_ACTIVE_BOOKINGS',
      activeBookings: 3,
    });

    expect(message).toBe(roomsText.activeBookings(3));
    expect(message).toContain('3');
  });

  it('uses the singular for a single booking', () => {
    expect(roomsText.activeBookings(1)).not.toBe(roomsText.activeBookings(2));
  });

  it('prefers the message of the first field error', () => {
    expect(
      messageForRoomsFailure({
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ code: 'TOO_LONG' }],
      }),
    ).toBe(roomsText.fieldErrors.TOO_LONG);
    expect(
      messageForRoomsFailure({
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ code: 'REQUIRED' }],
      }),
    ).toBe(roomsText.fieldErrors.REQUIRED);
  });

  it.each(['FORBIDDEN', 'NOT_FOUND'])('has a message for %s', (code) => {
    expect(messageForRoomsFailure({ code })).toBe(roomsText.errors[code]);
  });

  it.each(['BAD_REQUEST', 'INTERNAL_ERROR', undefined])(
    'shows the generic message for %s',
    (code) => {
      expect(messageForRoomsFailure({ code })).toBe(roomsText.errors.UNKNOWN);
    },
  );
});

describe('messageForRoomsFailure for a concurrent modification', () => {
  it('asks to reload and retry', () => {
    expect(messageForRoomsFailure({ code: 'CONCURRENT_MODIFICATION' })).toBe(
      'El salón ha cambiado mientras lo editabas; recarga y vuelve a intentarlo.',
    );
  });
});
