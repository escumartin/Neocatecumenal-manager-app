package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.PurgeExpiredEvents;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Event;
import io.github.escumartin.neocatecumenal.agenda.domain.EventId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class EventPurgeIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private EventRepository events;

    @Autowired
    private PurgeExpiredEvents purge;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduler;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
    }

    private Event on(LocalDate date) {
        return events.save(Event.restore(new EventId(UUID.randomUUID()), new CommunityId(UUID.randomUUID()),
                EventType.WORD, new EventTitle("Opening word"), date,
                new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0)), new RoomId(UUID.randomUUID()),
                new BookingRequestId(UUID.randomUUID()), new UserId(UUID.randomUUID()), Instant.now()));
    }

    @Test
    void anEventDatedMoreThanOneYearAgoIsDeleted() {
        Event expired = on(TODAY.minusYears(1).minusDays(1));

        purge.purge();

        assertThat(events.findById(expired.id())).isEmpty();
    }

    @Test
    void anEventDatedExactlyOneYearAgoIsKept() {
        Event boundary = on(TODAY.minusYears(1));

        purge.purge();

        assertThat(events.findById(boundary.id())).isPresent();
    }

    @Test
    void onlyTheExpiredEventsAreDeleted() {
        on(TODAY.minusYears(3));
        Event recent = on(TODAY.minusDays(5));
        Event upcoming = on(TODAY.plusDays(20));

        purge.purge();

        assertThat(jdbc.queryForObject("select count(*) from event", Integer.class)).isEqualTo(2);
        assertThat(events.findById(recent.id())).isPresent();
        assertThat(events.findById(upcoming.id())).isPresent();
    }

    @Test
    void thePurgeJobIsScheduledByTheApplication() {
        List<String> scheduled = scheduler.getScheduledTasks().stream()
                .map(task -> task.getTask().getRunnable().toString())
                .toList();

        assertThat(scheduled).anyMatch(runnable -> runnable.contains("EventPurgeJob"));
    }
}
