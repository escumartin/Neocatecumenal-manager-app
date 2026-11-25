package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import static io.github.escumartin.neocatecumenal.FlywayTestSupport.assertSqlState;
import static io.github.escumartin.neocatecumenal.FlywayTestSupport.migratedTemplate;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class AgendaSchemaMigrationIT {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String STRING_TOO_LONG = "22001";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        jdbc = migratedTemplate(POSTGRES);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate event");
    }

    private void insertEvent(String type, String title, String start, String end, UUID bookingRequestId) {
        jdbc.update("""
                insert into event (id, community_id, type, title, event_date, start_time, end_time, room_id,
                    booking_request_id, created_by, created_at)
                values (?, ?, ?, ?, '2030-05-10'::date, ?::time, ?::time, ?, ?, ?, now())
                """, UUID.randomUUID(), UUID.randomUUID(), type, title, start, end, UUID.randomUUID(),
                bookingRequestId, UUID.randomUUID());
    }

    @Test
    void storesAWordEventAndAConvivenceEvent() {
        insertEvent("WORD", "Evening word", "19:00", "20:30", UUID.randomUUID());
        insertEvent("CONVIVENCE", "Day together", "09:00", "18:00", UUID.randomUUID());

        assertThat(jdbc.queryForObject("select count(*) from event", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsAnUnknownEventType() {
        assertSqlState(() -> insertEvent("PARTY", "Evening word", "19:00", "20:30", UUID.randomUUID()),
                CHECK_VIOLATION);
    }

    @Test
    void rejectsATitleLongerThanOneHundredTwentyCharacters() {
        assertSqlState(() -> insertEvent("WORD", "x".repeat(121), "19:00", "20:30", UUID.randomUUID()),
                STRING_TOO_LONG);
    }

    @Test
    void acceptsATitleOfOneHundredTwentyCharacters() {
        insertEvent("WORD", "x".repeat(120), "19:00", "20:30", UUID.randomUUID());

        assertThat(jdbc.queryForObject("select count(*) from event", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsAnEventWhoseEndIsNotAfterItsStart() {
        assertSqlState(() -> insertEvent("WORD", "Evening word", "20:00", "20:00", UUID.randomUUID()),
                CHECK_VIOLATION);
    }

    @Test
    void rejectsTwoEventsForTheSameBookingRequest() {
        UUID bookingRequest = UUID.randomUUID();
        insertEvent("WORD", "Evening word", "19:00", "20:30", bookingRequest);

        assertSqlState(() -> insertEvent("WORD", "Other word", "21:00", "22:00", bookingRequest), UNIQUE_VIOLATION);
    }

    @Test
    void hasNoForeignKeyTowardsSpaces() {
        List<String> references = jdbc.queryForList("""
                select confrelid::regclass::text
                from pg_constraint
                where conrelid = 'event'::regclass and contype = 'f'
                """, String.class);

        assertThat(references).isEmpty();
    }
}
