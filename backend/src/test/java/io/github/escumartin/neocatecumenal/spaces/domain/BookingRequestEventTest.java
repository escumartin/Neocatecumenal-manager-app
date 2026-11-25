package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingRequestEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2030, 5, 14);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final RoomId ROOM = new RoomId(UUID.randomUUID());

    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);

    @Test
    void aRequestForAnEventKeepsItsEventId() {
        EventId event = new EventId(UUID.randomUUID());

        BookingRequest created = BookingRequest.forEvent(moderator.userId(), OWN, ROOM, DATE, SLOT,
                new Reason("Opening word"), event, NOW, AT);

        assertThat(created.eventId()).contains(event);
        assertThat(created.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(created.bookings()).hasSize(1);
        assertThat(created.requestedBy()).contains(moderator.userId());
        assertThat(created.communityId()).isEqualTo(OWN);
    }

    @Test
    void aRequestForAnEventRecordsTheCreatorInItsHistory() {
        BookingRequest created = BookingRequest.forEvent(moderator.userId(), OWN, ROOM, DATE, SLOT,
                new Reason("Opening word"), new EventId(UUID.randomUUID()), NOW, AT);

        assertThat(created.history()).singleElement().satisfies(change -> {
            assertThat(change.to()).isEqualTo(BookingRequestStatus.PENDING);
            assertThat(change.changedBy()).isEqualTo(moderator.userId());
        });
    }

    @Test
    void aRequestForAnEventStartingInThePastIsRejected() {
        assertThatThrownBy(() -> BookingRequest.forEvent(moderator.userId(), OWN, ROOM, NOW.toLocalDate(),
                new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)), new Reason("Opening word"),
                new EventId(UUID.randomUUID()), NOW, AT))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("START_NOT_IN_FUTURE"));
    }

    @Test
    void aPlainRequestHasNoEventId() {
        BookingRequest created = BookingRequest.request(moderator, ROOM, OWN, DATE, SLOT, new Reason("Catechesis"),
                NOW, AT);

        assertThat(created.eventId()).isEmpty();
    }

    @Test
    void aRestoredRequestKeepsItsEventId() {
        EventId event = new EventId(UUID.randomUUID());
        BookingRequest created = BookingRequest.forEvent(moderator.userId(), OWN, ROOM, DATE, SLOT,
                new Reason("Opening word"), event, NOW, AT);

        BookingRequest restored = BookingRequest.restore(created.id(), ROOM, OWN, moderator.userId(),
                created.reason(), null, DATE, DATE, SLOT, null, event, created.status(), created.bookings(),
                created.history());

        assertThat(restored.eventId()).contains(event);
    }
}
