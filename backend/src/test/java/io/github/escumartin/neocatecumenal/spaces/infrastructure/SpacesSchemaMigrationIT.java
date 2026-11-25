package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static io.github.escumartin.neocatecumenal.FlywayTestSupport.assertSqlState;
import static io.github.escumartin.neocatecumenal.FlywayTestSupport.migratedTemplate;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SpacesSchemaMigrationIT {

    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static JdbcTemplate jdbc;

    private UUID room;

    @BeforeAll
    static void migrate() {
        jdbc = migratedTemplate(POSTGRES);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = insertRoom("Main hall", true);
    }

    private UUID insertRoom(String name, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into room (id, name, active, version, created_at) values (?, ?, ?, 0, now())", id, name,
                active);
        return id;
    }

    private UUID insertRequest(UUID roomId, String firstDate, String lastDate, String start, String end,
            String status, UUID eventId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into booking_request (id, room_id, community_id, requested_by, event_id, reason, first_date,
                    last_date, start_time, end_time, status, version, created_at)
                values (?, ?, ?, ?, ?, 'Meeting', ?::date, ?::date, ?::time, ?::time, ?, 0, now())
                """, id, roomId, UUID.randomUUID(), UUID.randomUUID(), eventId, firstDate, lastDate, start, end,
                status);
        return id;
    }

    private UUID insertBooking(UUID requestId, UUID roomId, String date, String start, String end, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into booking (id, booking_request_id, room_id, booking_date, start_time, end_time, status)
                values (?, ?, ?, ?::date, ?::time, ?::time, ?)
                """, id, requestId, roomId, date, start, end, status);
        return id;
    }

    private UUID pendingBooking(UUID roomId, String date, String start, String end, String status) {
        UUID request = insertRequest(roomId, date, date, start, end, status, null);
        return insertBooking(request, roomId, date, start, end, status);
    }

    @Test
    void enablesTheBtreeGistExtension() {
        Integer installed = jdbc.queryForObject("select count(*) from pg_extension where extname = 'btree_gist'",
                Integer.class);

        assertThat(installed).isEqualTo(1);
    }

    @Test
    void rejectsOverlappingActiveBookingsInTheSameRoom() {
        pendingBooking(room, "2030-05-10", "10:00", "12:00", "APPROVED");

        assertSqlState(() -> pendingBooking(room, "2030-05-10", "11:00", "13:00", "PENDING"), EXCLUSION_VIOLATION);
    }

    @Test
    void allowsContiguousBookingsInTheSameRoom() {
        pendingBooking(room, "2030-05-10", "10:00", "12:00", "APPROVED");

        pendingBooking(room, "2030-05-10", "12:00", "14:00", "PENDING");

        assertThat(jdbc.queryForObject("select count(*) from booking", Integer.class)).isEqualTo(2);
    }

    @Test
    void allowsTheSameSlotInAnotherRoom() {
        UUID otherRoom = insertRoom("Chapel", true);
        pendingBooking(room, "2030-05-10", "10:00", "12:00", "APPROVED");

        pendingBooking(otherRoom, "2030-05-10", "10:00", "12:00", "APPROVED");

        assertThat(jdbc.queryForObject("select count(*) from booking", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectedAndCancelledBookingsDoNotBlockTheSlot() {
        pendingBooking(room, "2030-05-10", "10:00", "12:00", "REJECTED");
        pendingBooking(room, "2030-05-10", "10:00", "12:00", "CANCELLED");

        pendingBooking(room, "2030-05-10", "10:00", "12:00", "PENDING");

        assertThat(jdbc.queryForObject("select count(*) from booking", Integer.class)).isEqualTo(3);
    }

    @Test
    void pendingBookingsBlockTheSlot() {
        pendingBooking(room, "2030-05-10", "10:00", "12:00", "PENDING");

        assertSqlState(() -> pendingBooking(room, "2030-05-10", "10:30", "11:00", "APPROVED"), EXCLUSION_VIOLATION);
    }

    @Test
    void rejectsABookingWhoseEndIsNotAfterItsStart() {
        UUID request = insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "PENDING", null);

        assertSqlState(() -> insertBooking(request, room, "2030-05-10", "12:00", "12:00", "PENDING"),
                CHECK_VIOLATION);
    }

    @Test
    void rejectsARequestWhoseEndIsNotAfterItsStart() {
        assertSqlState(() -> insertRequest(room, "2030-05-10", "2030-05-10", "12:00", "11:00", "PENDING", null),
                CHECK_VIOLATION);
    }

    @Test
    void rejectsARequestWhoseLastDateIsBeforeItsFirstDate() {
        assertSqlState(() -> insertRequest(room, "2030-05-10", "2030-05-09", "10:00", "12:00", "PENDING", null),
                CHECK_VIOLATION);
    }

    @Test
    void rejectsAnUnknownRequestStatus() {
        assertSqlState(() -> insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "EXPIRED", null),
                CHECK_VIOLATION);
    }

    @Test
    void rejectsAnUnknownBookingStatus() {
        UUID request = insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "PENDING", null);

        assertSqlState(() -> insertBooking(request, room, "2030-05-10", "10:00", "12:00", "EXPIRED"),
                CHECK_VIOLATION);
    }

    @Test
    void rejectsTheSameActiveRoomNameIgnoringCase() {
        assertSqlState(() -> insertRoom("MAIN HALL", true), UNIQUE_VIOLATION);
    }

    @Test
    void allowsReusingTheNameOfAnInactiveRoom() {
        insertRoom("Old room", false);

        insertRoom("old room", true);

        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isEqualTo(3);
    }

    @Test
    void rejectsTheSameEventOnTwoRequests() {
        UUID event = UUID.randomUUID();
        insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "11:00", "PENDING", event);

        assertSqlState(() -> insertRequest(room, "2030-05-11", "2030-05-11", "10:00", "11:00", "PENDING", event),
                UNIQUE_VIOLATION);
    }

    @Test
    void allowsManyRequestsWithoutEvent() {
        insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "11:00", "PENDING", null);
        insertRequest(room, "2030-05-11", "2030-05-11", "10:00", "11:00", "PENDING", null);

        assertThat(jdbc.queryForObject("select count(*) from booking_request", Integer.class)).isEqualTo(2);
    }

    @Test
    void deletingARequestCascadesToItsBookingsAndHistory() {
        UUID request = insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "PENDING", null);
        UUID booking = insertBooking(request, room, "2030-05-10", "10:00", "12:00", "PENDING");
        jdbc.update("""
                insert into booking_status_change (booking_request_id, booking_id, from_status, to_status, changed_by,
                    changed_at)
                values (?, ?, null, 'PENDING', ?, now())
                """, request, booking, UUID.randomUUID());
        jdbc.update("""
                insert into booking_status_change (booking_request_id, booking_id, from_status, to_status, changed_by,
                    changed_at)
                values (?, null, null, 'PENDING', null, now())
                """, request);

        jdbc.update("delete from booking_request where id = ?", request);

        assertThat(jdbc.queryForObject("select count(*) from booking", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from booking_status_change", Integer.class)).isZero();
    }

    @Test
    void rejectsABookingOfAnUnknownRoom() {
        UUID request = insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "PENDING", null);

        assertSqlState(() -> insertBooking(request, UUID.randomUUID(), "2030-05-10", "10:00", "12:00", "PENDING"),
                FOREIGN_KEY_VIOLATION);
    }

    @Test
    void rejectsAnUnknownStatusInTheHistoryOrigin() {
        UUID request = insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "PENDING", null);

        assertSqlState(() -> insertStatusChange(request, "EXPIRED", "PENDING"), CHECK_VIOLATION);
    }

    @Test
    void rejectsAnUnknownStatusInTheHistoryTarget() {
        UUID request = insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "PENDING", null);

        assertSqlState(() -> insertStatusChange(request, "PENDING", "EXPIRED"), CHECK_VIOLATION);
    }

    @Test
    void acceptsAHistoryEntryWithoutOrigin() {
        UUID request = insertRequest(room, "2030-05-10", "2030-05-10", "10:00", "12:00", "PENDING", null);

        insertStatusChange(request, null, "PENDING");

        assertThat(jdbc.queryForObject("select count(*) from booking_status_change", Integer.class)).isEqualTo(1);
    }

    private void insertStatusChange(UUID requestId, String from, String to) {
        jdbc.update("""
                insert into booking_status_change (booking_request_id, from_status, to_status, changed_at)
                values (?, ?, ?, now())
                """, requestId, from, to);
    }
}
