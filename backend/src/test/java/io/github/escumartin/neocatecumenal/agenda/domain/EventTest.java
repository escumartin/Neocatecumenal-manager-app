package io.github.escumartin.neocatecumenal.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 9, 30);
    private static final Instant AT = Instant.parse("2030-05-10T07:30:00Z");
    private static final LocalDate TOMORROW = LocalDate.of(2030, 5, 11);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());
    private static final Actor MODERATOR = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY);

    private static Event eventOn(LocalDate date, TimeSlot slot) {
        return Event.create(new EventId(UUID.randomUUID()), MODERATOR, COMMUNITY, EventType.WORD,
                new EventTitle("Opening word"), date, slot, new RoomId(UUID.randomUUID()),
                new BookingRequestId(UUID.randomUUID()), NOW, AT);
    }

    private static void assertInvalid(Runnable action, String field, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AgendaFieldInvalid.class, exception -> {
            assertThat(exception.field()).isEqualTo(field);
            assertThat(exception.code()).isEqualTo(code);
        });
    }

    @Test
    void titleOfOneCharacterIsValid() {
        assertThat(new EventTitle("A").value()).isEqualTo("A");
    }

    @Test
    void titleOfOneHundredAndTwentyCharactersIsValid() {
        assertThat(new EventTitle("x".repeat(120)).value()).hasSize(120);
    }

    @Test
    void titleOfOneHundredAndTwentyOneCharactersIsTooLong() {
        assertInvalid(() -> new EventTitle("x".repeat(121)), "title", "TOO_LONG");
    }

    @Test
    void blankTitleIsRequired() {
        assertInvalid(() -> new EventTitle("   "), "title", "REQUIRED");
    }

    @Test
    void nullTitleIsRequired() {
        assertInvalid(() -> new EventTitle(null), "title", "REQUIRED");
    }

    @Test
    void titleIsStripped() {
        assertThat(new EventTitle("  Opening word  ").value()).isEqualTo("Opening word");
    }

    @Test
    void titleLengthCountsCodePoints() {
        assertThat(new EventTitle("😀".repeat(120)).value()).isNotBlank();
    }

    @Test
    void eventTypesAreWordAndConvivence() {
        assertThat(EventType.values()).containsExactly(EventType.WORD, EventType.CONVIVENCE);
    }

    @Test
    void slotEndMustBeAfterStart() {
        assertInvalid(() -> new TimeSlot(LocalTime.of(20, 0), LocalTime.of(18, 0)), "endTime",
                "END_NOT_AFTER_START");
    }

    @Test
    void slotWithEqualStartAndEndIsInvalid() {
        assertInvalid(() -> new TimeSlot(LocalTime.of(18, 0), LocalTime.of(18, 0)), "endTime",
                "END_NOT_AFTER_START");
    }

    @Test
    void slotRejectsSecondsPrecision() {
        assertInvalid(() -> new TimeSlot(LocalTime.of(18, 0, 30), LocalTime.of(20, 0)), "startTime",
                "INVALID_FORMAT");
    }

    @Test
    void createdEventKeepsItsData() {
        EventId id = new EventId(UUID.randomUUID());
        RoomId room = new RoomId(UUID.randomUUID());
        BookingRequestId request = new BookingRequestId(UUID.randomUUID());

        Event event = Event.create(id, MODERATOR, COMMUNITY, EventType.CONVIVENCE, new EventTitle("Day trip"),
                TOMORROW, SLOT, room, request, NOW, AT);

        assertThat(event.id()).isEqualTo(id);
        assertThat(event.communityId()).isEqualTo(COMMUNITY);
        assertThat(event.type()).isEqualTo(EventType.CONVIVENCE);
        assertThat(event.title()).isEqualTo(new EventTitle("Day trip"));
        assertThat(event.date()).isEqualTo(TOMORROW);
        assertThat(event.slot()).isEqualTo(SLOT);
        assertThat(event.roomId()).isEqualTo(room);
        assertThat(event.bookingRequestId()).isEqualTo(request);
        assertThat(event.createdBy()).contains(MODERATOR.userId());
        assertThat(event.createdAt()).isEqualTo(AT);
    }

    @Test
    void startLaterTodayIsInTheFuture() {
        Event event = eventOn(LocalDate.of(2030, 5, 10), new TimeSlot(LocalTime.of(9, 31), LocalTime.of(10, 0)));

        assertThat(event.date()).isEqualTo(LocalDate.of(2030, 5, 10));
    }

    @Test
    void startExactlyNowIsNotInTheFuture() {
        assertInvalid(() -> eventOn(LocalDate.of(2030, 5, 10), new TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 0))),
                "startTime", "START_NOT_IN_FUTURE");
    }

    @Test
    void startInThePastIsNotInTheFuture() {
        assertInvalid(() -> eventOn(LocalDate.of(2030, 5, 9), SLOT), "startTime", "START_NOT_IN_FUTURE");
    }

    @Test
    void requireStartInFutureAppliesTheSameRuleWithoutBuildingAnEvent() {
        assertInvalid(() -> Event.requireStartInFuture(LocalDate.of(2030, 5, 9), SLOT, NOW), "startTime",
                "START_NOT_IN_FUTURE");
        Event.requireStartInFuture(TOMORROW, SLOT, NOW);
    }

    @Test
    void restoredEventWithoutCreatorHasNoCreatedBy() {
        Event restored = Event.restore(new EventId(UUID.randomUUID()), COMMUNITY, EventType.WORD,
                new EventTitle("Opening word"), TOMORROW, SLOT, new RoomId(UUID.randomUUID()),
                new BookingRequestId(UUID.randomUUID()), null, AT);

        assertThat(restored.createdBy()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc\u0000def", "abc\u0007", "\u001bboo", "line\nbreak", "tab\there"})
    void titleWithControlCharactersIsInvalidFormat(String value) {
        assertInvalid(() -> new EventTitle(value), "title", "INVALID_FORMAT");
    }
}
