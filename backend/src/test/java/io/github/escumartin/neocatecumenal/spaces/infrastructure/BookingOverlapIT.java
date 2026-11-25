package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class BookingOverlapIT {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2030, 5, 11);
    private static final TimeSlot EXISTING = slot(18, 0, 20, 0);
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private BookingRequestRepository requests;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
            COMMUNITY);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY);

    private Room room;

    private static TimeSlot slot(int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeSlot(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
        requests.save(newRequest(room, DAY, EXISTING));
    }

    private BookingRequest newRequest(Room target, LocalDate date, TimeSlot slot) {
        return BookingRequest.request(moderator, target.id(), COMMUNITY, date, slot, new Reason("Catechesis"), NOW,
                AT);
    }

    private int bookings() {
        return jdbc.queryForObject("select count(*) from booking", Integer.class);
    }

    @Test
    void anOverlappingSlotStartingInsideIsRejected() {
        assertThatThrownBy(() -> requests.save(newRequest(room, DAY, slot(19, 0, 21, 0))))
                .isInstanceOf(RoomOccupied.class);
    }

    @Test
    void anOverlappingSlotEndingInsideIsRejected() {
        assertThatThrownBy(() -> requests.save(newRequest(room, DAY, slot(17, 0, 18, 30))))
                .isInstanceOf(RoomOccupied.class);
    }

    @Test
    void anEnvelopingSlotIsRejected() {
        assertThatThrownBy(() -> requests.save(newRequest(room, DAY, slot(17, 0, 21, 0))))
                .isInstanceOf(RoomOccupied.class);
    }

    @Test
    void theRaceFailureCarriesNoConflicts() {
        assertThatThrownBy(() -> requests.save(newRequest(room, DAY, slot(19, 0, 21, 0))))
                .isInstanceOfSatisfying(RoomOccupied.class, failure -> assertThat(failure.conflicts()).isEmpty());
    }

    @Test
    void contiguousSlotsAreSaved() {
        requests.save(newRequest(room, DAY, slot(20, 0, 21, 0)));
        requests.save(newRequest(room, DAY, slot(16, 0, 18, 0)));

        assertThat(bookings()).isEqualTo(3);
    }

    @Test
    void anotherRoomIsSaved() {
        Room other = rooms.save(Room.create(administrator, new RoomName("Small hall")));

        requests.save(newRequest(other, DAY, EXISTING));

        assertThat(bookings()).isEqualTo(2);
    }

    @Test
    void aRejectedFailureRollsBackTheWholeTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            requests.save(newRequest(room, DAY, slot(20, 0, 21, 0)));
            requests.save(newRequest(room, DAY, slot(19, 0, 20, 30)));
        })).isInstanceOf(RoomOccupied.class);

        assertThat(bookings()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from booking_request", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectedOccurrencesDoNotBlockTheSlot() {
        jdbc.update("update booking set status = 'REJECTED'");

        requests.save(newRequest(room, DAY, EXISTING));

        assertThat(bookings()).isEqualTo(2);
    }

    @Test
    void cancelledOccurrencesDoNotBlockTheSlot() {
        jdbc.update("update booking set status = 'CANCELLED'");

        requests.save(newRequest(room, DAY, EXISTING));

        assertThat(bookings()).isEqualTo(2);
    }

    @Test
    void findConflictsReturnsTheOverlappingSlotsOfThoseDays() {
        requests.save(newRequest(room, DAY.plusDays(1), slot(9, 0, 10, 0)));

        List<Conflict> conflicts = requests.findConflicts(room.id(), List.of(DAY, DAY.plusDays(1)), slot(9, 30, 19, 0));

        assertThat(conflicts).containsExactly(new Conflict(DAY, EXISTING),
                new Conflict(DAY.plusDays(1), slot(9, 0, 10, 0)));
    }

    @Test
    void findConflictsIgnoresContiguousOtherRoomOtherDayAndInactiveOccurrences() {
        Room other = rooms.save(Room.create(administrator, new RoomName("Small hall")));
        requests.save(newRequest(other, DAY, EXISTING));
        jdbc.update("update booking set status = 'CANCELLED' where room_id = ?", other.id().value());

        assertThat(requests.findConflicts(room.id(), List.of(DAY), slot(20, 0, 21, 0))).isEmpty();
        assertThat(requests.findConflicts(room.id(), List.of(DAY.plusDays(1)), EXISTING)).isEmpty();
        assertThat(requests.findConflicts(other.id(), List.of(DAY), EXISTING)).isEmpty();
    }
}
