import { bookingsText } from '../bookings';
import { eventsText, messageForEventFailure } from '../events';

describe('messageForEventFailure', () => {
  it.each([
    ['title', 'REQUIRED'],
    ['title', 'TOO_LONG'],
    ['type', 'INVALID_FORMAT'],
    ['startTime', 'START_NOT_IN_FUTURE'],
    ['endTime', 'END_NOT_AFTER_START'],
    ['communityId', 'COMMUNITY_REQUIRED'],
    ['communityId', 'COMMUNITY_NOT_FOUND'],
    ['date', 'INVALID_FORMAT'],
  ])('explains the field error %s %s', (field, code) => {
    const message = messageForEventFailure({
      code: 'VALIDATION_FAILED',
      fieldErrors: [{ field, code }],
    });

    expect(message).not.toBe(eventsText.errors.UNKNOWN);
    expect(message).not.toBe(eventsText.errors.VALIDATION_FAILED);
  });

  it('says the title is too long with its limit', () => {
    expect(
      messageForEventFailure({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'title', code: 'TOO_LONG' }] }),
    ).toContain('120');
  });

  it('does not talk about a reason when the title is missing', () => {
    expect(
      messageForEventFailure({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'title', code: 'REQUIRED' }] }),
    ).toBe(eventsText.fieldErrors.TITLE_REQUIRED);
  });

  it.each(['ROOM_INACTIVE', 'FORBIDDEN', 'NOT_FOUND', 'VALIDATION_FAILED'])('explains the error code %s', (code) => {
    const message = messageForEventFailure({ code });

    expect(message).toBe(eventsText.errors[code]);
    expect(message).not.toBe(eventsText.errors.UNKNOWN);
  });

  it('falls back to a generic message for an unknown code', () => {
    expect(messageForEventFailure({ code: 'SURPRISE' })).toBe(eventsText.errors.UNKNOWN);
    expect(messageForEventFailure({})).toBe(eventsText.errors.UNKNOWN);
  });

  it('lists every conflicting date of an occupied room', () => {
    const conflicts = [
      { date: '2030-05-11', startTime: '18:00', endTime: '20:00' },
      { date: '2030-05-18', startTime: '18:00', endTime: '20:00' },
    ];

    expect(messageForEventFailure({ code: 'ROOM_OCCUPIED', conflicts })).toBe(bookingsText.occupied.withDates(conflicts));
  });

  it('asks to look again when the room was taken without known dates', () => {
    expect(messageForEventFailure({ code: 'ROOM_OCCUPIED', conflicts: [] })).toBe(bookingsText.occupied.withoutDates);
  });
});

describe('eventsText', () => {
  it('names both event types in Spanish', () => {
    expect(eventsText.types.WORD).toBe('Palabra');
    expect(eventsText.types.CONVIVENCE).toBe('Convivencia');
  });

  it('formats the date of an event as day, month and year', () => {
    expect(eventsText.detail.when({ date: '2030-05-11', startTime: '18:00', endTime: '20:00' })).toBe(
      '11/05/2030, de 18:00 a 20:00',
    );
  });

  it('announces the status of the booking request of a new event', () => {
    expect(eventsText.create.sent('PENDING')).toContain(bookingsText.status.PENDING);
  });
});

describe('messageForEventFailure for a title with control characters', () => {
  it('says the title has invalid characters', () => {
    expect(
      messageForEventFailure({
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'title', code: 'INVALID_FORMAT' }],
      }),
    ).toBe('El título contiene caracteres no válidos.');
  });
});
