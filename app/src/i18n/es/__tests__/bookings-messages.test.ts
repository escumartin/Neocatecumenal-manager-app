import {
  bookingsText,
  messageForBookingFailure,
  messageForResolutionFailure,
} from "../bookings";

describe("messageForBookingFailure", () => {
  it.each([
    "START_NOT_IN_FUTURE",
    "END_NOT_AFTER_START",
    "COMMUNITY_REQUIRED",
    "COMMUNITY_NOT_FOUND",
  ])("explains the field error %s", (code) => {
    const message = messageForBookingFailure({
      code: "VALIDATION_FAILED",
      fieldErrors: [{ code }],
    });

    expect(message).toBe(bookingsText.fieldErrors[code]);
    expect(message).not.toBe(bookingsText.errors.UNKNOWN);
  });

  it.each(["REQUIRED", "TOO_LONG", "INVALID_FORMAT"])(
    "explains the generic field error %s",
    (code) => {
      expect(
        messageForBookingFailure({
          code: "VALIDATION_FAILED",
          fieldErrors: [{ code }],
        }),
      ).toBe(bookingsText.fieldErrors[code]);
    },
  );

  it.each([
    "ROOM_INACTIVE",
    "BOOKING_REQUEST_ALREADY_RESOLVED",
    "BOOKING_ALREADY_STARTED",
    "BOOKING_REQUEST_NOT_ACTIVE",
    "FORBIDDEN",
    "NOT_FOUND",
  ])("explains the problem code %s", (code) => {
    expect(messageForBookingFailure({ code })).toBe(bookingsText.errors[code]);
  });

  it("uses the generic message for an unknown code or no code", () => {
    expect(messageForBookingFailure({ code: "SOMETHING_ELSE" })).toBe(
      bookingsText.errors.UNKNOWN,
    );
    expect(messageForBookingFailure({})).toBe(bookingsText.errors.UNKNOWN);
  });

  it("warns not to include personal data in the reason", () => {
    expect(bookingsText.reasonWarning.toLowerCase()).toContain(
      "datos personales",
    );
  });

  it("names every status of a request", () => {
    for (const status of [
      "PENDING",
      "APPROVED",
      "REJECTED",
      "CANCELLED",
    ] as const) {
      expect(bookingsText.status[status]).toBeTruthy();
    }
  });
});

describe("room occupied messages", () => {
  const conflict = { date: "2030-05-11", startTime: "18:00", endTime: "20:00" };

  it("lists the conflicting dates and slots", () => {
    const message = messageForBookingFailure({
      code: "ROOM_OCCUPIED",
      conflicts: [conflict],
    });

    expect(message).toContain("11/05/2030");
    expect(message).toContain("18:00");
    expect(message).toContain("20:00");
  });

  it("lists every conflict", () => {
    const message = messageForBookingFailure({
      code: "ROOM_OCCUPIED",
      conflicts: [
        conflict,
        { date: "2030-05-18", startTime: "09:00", endTime: "10:00" },
      ],
    });

    expect(message).toContain("11/05/2030");
    expect(message).toContain("18/05/2030");
  });

  it("asks to reload the occupancy when there are no conflicts", () => {
    const expected = bookingsText.occupied.withoutDates;

    expect(
      messageForBookingFailure({ code: "ROOM_OCCUPIED", conflicts: [] }),
    ).toBe(expected);
    expect(messageForBookingFailure({ code: "ROOM_OCCUPIED" })).toBe(expected);
    expect(expected.toLowerCase()).toContain("actualiza");
  });

  it("labels the occupancy of a slot with its status and community", () => {
    expect(
      bookingsText.occupancy.slotLabel({
        startTime: "18:00",
        endTime: "20:00",
        status: "APPROVED",
        ownCommunity: true,
      }),
    ).toBe("De 18:00 a 20:00, Aprobada, tu comunidad");
    expect(
      bookingsText.occupancy.slotLabel({
        startTime: "09:00",
        endTime: "10:00",
        status: "PENDING",
        ownCommunity: false,
      }),
    ).toBe("De 09:00 a 10:00, Pendiente, otra comunidad");
  });
});

describe("messageForResolutionFailure", () => {
  it("explains that a started request can only be rejected", () => {
    const message = messageForResolutionFailure({
      code: "VALIDATION_FAILED",
      fieldErrors: [{ code: "START_NOT_IN_FUTURE" }],
    });

    expect(message).toBe(bookingsText.resolve.alreadyStarted);
    expect(message.toLowerCase()).toContain("rechazar");
  });

  it("explains a request that another administrator resolved first", () => {
    expect(
      messageForResolutionFailure({ code: "BOOKING_REQUEST_ALREADY_RESOLVED" }),
    ).toBe(bookingsText.resolve.alreadyResolved);
  });

  it("falls back to the generic booking messages", () => {
    expect(messageForResolutionFailure({ code: "FORBIDDEN" })).toBe(
      bookingsText.errors.FORBIDDEN,
    );
    expect(messageForResolutionFailure({})).toBe(bookingsText.errors.UNKNOWN);
  });
});

describe("cancellation messages", () => {
  it.each(["BOOKING_ALREADY_STARTED", "BOOKING_REQUEST_NOT_ACTIVE"])(
    "explains the refusal %s",
    (code) => {
      const message = messageForBookingFailure({ code });

      expect(message).toBe(bookingsText.errors[code]);
      expect(message).not.toBe(bookingsText.errors.UNKNOWN);
    },
  );

  it("warns that the cancellation is permanent and frees the slot", () => {
    expect(bookingsText.cancel.warning).toContain("definitiva");
    expect(bookingsText.cancel.warning).toContain("libre");
  });

  it("names the room in the action and in the title", () => {
    expect(bookingsText.cancel.actionFor("Salón grande")).toContain(
      "Salón grande",
    );
    expect(bookingsText.cancel.title("Salón grande")).toContain("Salón grande");
  });
});

describe("messageForBookingFailure for a weekly series", () => {
  it.each([
    "REQUIRED",
    "INVALID_FORMAT",
    "RECURRENCE_TOO_LONG",
    "RECURRENCE_UNTIL_BEFORE_DATE",
  ])("explains the end date error %s", (code) => {
    const message = messageForBookingFailure({
      code: "VALIDATION_FAILED",
      fieldErrors: [{ field: "recurrence.until", code }],
    });

    expect(message).toBe(bookingsText.recurrence.fieldErrors[code]);
    expect(message).not.toBe(bookingsText.errors.UNKNOWN);
  });

  it("does not confuse a missing end date with a missing reason", () => {
    expect(
      messageForBookingFailure({
        code: "VALIDATION_FAILED",
        fieldErrors: [{ field: "recurrence.until", code: "REQUIRED" }],
      }),
    ).not.toBe(bookingsText.fieldErrors.REQUIRED);
  });

  it("keeps the generic reason message for a missing reason", () => {
    expect(
      messageForBookingFailure({
        code: "VALIDATION_FAILED",
        fieldErrors: [{ field: "reason", code: "REQUIRED" }],
      }),
    ).toBe(bookingsText.fieldErrors.REQUIRED);
  });

  it("says that the whole series is refused and lists every conflicting date", () => {
    const message = messageForBookingFailure({
      code: "ROOM_OCCUPIED",
      conflicts: [
        { date: "2030-05-11", startTime: "18:00", endTime: "20:00" },
        { date: "2030-05-25", startTime: "18:00", endTime: "20:00" },
      ],
    });

    expect(message).toContain("11/05/2030");
    expect(message).toContain("25/05/2030");
  });

  it("names the weekly repetition and its limits", () => {
    expect(bookingsText.recurrence.toggle.toLowerCase()).toContain("semana");
    expect(bookingsText.recurrence.hint.toLowerCase()).toContain("año");
  });
});

describe("messageForBookingFailure for a reason with control characters", () => {
  const failure = {
    code: "VALIDATION_FAILED",
    fieldErrors: [{ field: "reason", code: "INVALID_FORMAT" }],
  };

  it("says the reason has invalid characters", () => {
    expect(messageForBookingFailure(failure)).toBe(
      "El motivo contiene caracteres no válidos.",
    );
  });

  it("says the same when rejecting a request", () => {
    expect(messageForResolutionFailure(failure)).toBe(
      "El motivo contiene caracteres no válidos.",
    );
  });

  it("keeps the date message for other invalid format fields", () => {
    expect(
      messageForBookingFailure({
        code: "VALIDATION_FAILED",
        fieldErrors: [{ field: "date", code: "INVALID_FORMAT" }],
      }),
    ).toBe(bookingsText.fieldErrors.INVALID_FORMAT);
  });
});
