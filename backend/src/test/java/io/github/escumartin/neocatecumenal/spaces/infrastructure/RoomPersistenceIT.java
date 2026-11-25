package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static io.github.escumartin.neocatecumenal.FlywayTestSupport.assertSqlState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingCounter;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomConcurrentlyModified;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNameTaken;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class RoomPersistenceIT {

    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private BookingCounter counter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate otherConnection;

    private final Actor admin = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
            new CommunityId(UUID.randomUUID()));

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        otherConnection = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private Room newRoom(String name) {
        return rooms.save(Room.create(admin, new RoomName(name)));
    }

    private void bookingOccurrence(RoomId room, String date, String start, String end, String status) {
        UUID request = UUID.randomUUID();
        jdbc.update("insert into booking_request (id, room_id, community_id, reason, first_date, last_date, "
                + "start_time, end_time, status, version, created_at) "
                + "values (?, ?, ?, 'Meeting', ?::date, ?::date, ?::time, ?::time, ?, 0, now())",
                request, room.value(), UUID.randomUUID(), date, date, start, end, status);
        jdbc.update("insert into booking (id, booking_request_id, room_id, booking_date, start_time, end_time, "
                + "status) values (?, ?, ?, ?::date, ?::time, ?::time, ?)",
                UUID.randomUUID(), request, room.value(), date, start, end, status);
    }

    @Test
    void savesAndFindsARoom() {
        Room saved = newRoom("Main hall");

        Room found = rooms.findById(saved.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.name()).isEqualTo(new RoomName("Main hall"));
        assertThat(found.active()).isTrue();
    }

    @Test
    void findByIdOfAnUnknownRoomIsEmpty() {
        assertThat(rooms.findById(new RoomId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void savingAnExistingRoomUpdatesItInPlace() {
        Room room = newRoom("Main hall");
        room.rename(admin, new RoomName("Side hall"));
        rooms.save(room);
        room.deactivate(admin, 0);
        rooms.save(room);

        Room found = rooms.findById(room.id()).orElseThrow();

        assertThat(found.name()).isEqualTo(new RoomName("Side hall"));
        assertThat(found.active()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from room", Long.class)).isEqualTo(2L);
    }

    @Test
    void refusesASecondActiveRoomWithTheSameNameIgnoringCase() {
        newRoom("Main hall");

        assertThatThrownBy(() -> newRoom("MAIN HALL")).isInstanceOf(RoomNameTaken.class);
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isEqualTo(1);
    }

    @Test
    void reusesTheNameOfADeactivatedRoom() {
        Room old = newRoom("Main hall");
        old.deactivate(admin, 0);
        rooms.save(old);

        Room reused = newRoom("Main hall");

        assertThat(reused.active()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isEqualTo(2);
    }

    @Test
    void existsActiveByNameIgnoresCaseAndInactiveRooms() {
        Room old = newRoom("Old hall");
        old.deactivate(admin, 0);
        rooms.save(old);
        newRoom("Main hall");

        assertThat(rooms.existsActiveByName(new RoomName("main HALL"))).isTrue();
        assertThat(rooms.existsActiveByName(new RoomName("Old hall"))).isFalse();
        assertThat(rooms.existsActiveByName(new RoomName("Unknown"))).isFalse();
    }

    @Test
    void findAllActiveSkipsDeactivatedRoomsAndSortsByName() {
        newRoom("Side hall");
        newRoom("Main hall");
        Room removed = newRoom("Old hall");
        removed.deactivate(admin, 0);
        rooms.save(removed);

        assertThat(rooms.findAllActive()).extracting(room -> room.name().value())
                .containsExactly("Main hall", "Side hall");
    }

    @Test
    void findByIdForUpdateHoldsAnExclusiveRowLock() {
        Room room = newRoom("Main hall");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            assertThat(rooms.findByIdForUpdate(room.id())).isPresent();

            assertSqlState(() -> otherConnection.queryForList(
                    "select id from room where id = ? for share nowait", room.id().value()), LOCK_NOT_AVAILABLE);
        });
        assertThat(otherConnection.queryForList("select id from room where id = ? for update nowait",
                room.id().value())).hasSize(1);
    }

    @Test
    void findByIdForShareBlocksWritersButAllowsOtherReaders() {
        Room room = newRoom("Main hall");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            assertThat(rooms.findByIdForShare(room.id())).isPresent();

            assertSqlState(() -> otherConnection.queryForList(
                    "select id from room where id = ? for update nowait", room.id().value()), LOCK_NOT_AVAILABLE);
            assertThat(otherConnection.queryForList("select id from room where id = ? for share nowait",
                    room.id().value())).hasSize(1);
        });
    }

    @Test
    void lockedLookupsOfAnUnknownRoomAreEmpty() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        RoomId unknown = new RoomId(UUID.randomUUID());

        transaction.executeWithoutResult(status -> {
            assertThat(rooms.findByIdForUpdate(unknown)).isEmpty();
            assertThat(rooms.findByIdForShare(unknown)).isEmpty();
        });
    }

    @Test
    void counterCountsPendingAndApprovedOccurrencesStartingAfterNow() {
        Room room = newRoom("Main hall");
        bookingOccurrence(room.id(), "2030-05-11", "10:00", "11:00", "PENDING");
        bookingOccurrence(room.id(), "2030-05-12", "10:00", "11:00", "APPROVED");

        assertThat(counter.activeFutureBookings(room.id(), NOW)).isEqualTo(2);
    }

    @Test
    void counterIgnoresRejectedAndCancelledOccurrences() {
        Room room = newRoom("Main hall");
        bookingOccurrence(room.id(), "2030-05-11", "10:00", "11:00", "REJECTED");
        bookingOccurrence(room.id(), "2030-05-12", "10:00", "11:00", "CANCELLED");

        assertThat(counter.activeFutureBookings(room.id(), NOW)).isZero();
    }

    @Test
    void counterIgnoresPastAndAlreadyStartedOccurrences() {
        Room room = newRoom("Main hall");
        bookingOccurrence(room.id(), "2030-05-09", "10:00", "11:00", "APPROVED");
        bookingOccurrence(room.id(), "2030-05-10", "09:00", "09:30", "PENDING");
        bookingOccurrence(room.id(), "2030-05-10", "10:00", "11:00", "APPROVED");

        assertThat(counter.activeFutureBookings(room.id(), NOW)).isZero();
    }

    @Test
    void counterCountsAnOccurrenceStartingTheSameDayLater() {
        Room room = newRoom("Main hall");
        bookingOccurrence(room.id(), "2030-05-10", "10:01", "11:00", "APPROVED");

        assertThat(counter.activeFutureBookings(room.id(), NOW)).isEqualTo(1);
    }

    @Test
    void counterOnlyCountsTheRequestedRoom() {
        Room room = newRoom("Main hall");
        Room other = newRoom("Side hall");
        bookingOccurrence(other.id(), "2030-05-11", "10:00", "11:00", "APPROVED");

        assertThat(counter.activeFutureBookings(room.id(), NOW)).isZero();
        assertThat(counter.activeFutureBookings(other.id(), NOW)).isEqualTo(1);
    }

    @Test
    void savingARoomChangedByAnotherTransactionReportsAConcurrentModification() {
        Room room = newRoom("Main hall");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Room loaded = rooms.findById(room.id()).orElseThrow();
            otherConnection.update("update room set version = version + 1 where id = ?", room.id().value());
            loaded.rename(admin, new RoomName("Renamed hall"));
            rooms.save(loaded);
        })).isInstanceOf(RoomConcurrentlyModified.class);
    }
}
