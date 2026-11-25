package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingRequestSeriesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final LocalDate FIRST = LocalDate.of(2030, 5, 14);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final RoomId ROOM = new RoomId(UUID.randomUUID());

    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);

    private BookingRequest series(WeeklyRecurrence recurrence) {
        return BookingRequest.request(moderator, ROOM, OWN, FIRST, SLOT, new Reason("Catechesis"), recurrence, NOW,
                AT);
    }

    @Test
    void eightWeeksGiveEightPendingOccurrencesWithTheSameSlot() {
        BookingRequest created = series(new WeeklyRecurrence(FIRST.plusWeeks(7)));

        assertThat(created.bookings()).hasSize(8);
        assertThat(created.bookings()).allSatisfy(booking -> {
            assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
            assertThat(booking.slot()).isEqualTo(SLOT);
        });
        assertThat(created.bookings().get(3).date()).isEqualTo(FIRST.plusWeeks(3));
    }

    @Test
    void theLastDateIsThatOfTheLastOccurrence() {
        BookingRequest created = series(new WeeklyRecurrence(FIRST.plusWeeks(7).plusDays(3)));

        assertThat(created.firstDate()).isEqualTo(FIRST);
        assertThat(created.lastDate()).isEqualTo(FIRST.plusWeeks(7));
    }

    @Test
    void theRequestKeepsItsRecurrence() {
        WeeklyRecurrence recurrence = new WeeklyRecurrence(FIRST.plusWeeks(7));

        assertThat(series(recurrence).recurrence()).contains(recurrence);
    }

    @Test
    void aSimpleRequestIsASeriesOfOneWithoutRecurrence() {
        BookingRequest created = series(null);

        assertThat(created.bookings()).hasSize(1);
        assertThat(created.recurrence()).isEmpty();
        assertThat(created.lastDate()).isEqualTo(FIRST);
    }

    @Test
    void aSeriesWhoseFirstOccurrenceHasStartedIsRejected() {
        assertThatThrownBy(() -> BookingRequest.request(moderator, ROOM, OWN, NOW.toLocalDate(),
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)), new Reason("Catechesis"),
                new WeeklyRecurrence(FIRST), NOW, AT)).isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        invalid -> assertThat(invalid.code()).isEqualTo("START_NOT_IN_FUTURE"));
    }

    @Test
    void aSeriesTooLongIsRejected() {
        assertThatThrownBy(() -> series(new WeeklyRecurrence(FIRST.plusYears(1).plusDays(1))))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        invalid -> assertThat(invalid.code()).isEqualTo("RECURRENCE_TOO_LONG"));
    }
}
