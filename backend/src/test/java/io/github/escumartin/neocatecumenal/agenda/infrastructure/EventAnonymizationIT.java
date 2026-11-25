package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

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
import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class EventAnonymizationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @MockitoSpyBean
    private EventRepository events;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    private final UserId deleted = new UserId(UUID.randomUUID());
    private final UserId other = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
    }

    private Event storedBy(UserId creator) {
        return events.save(Event.restore(new EventId(UUID.randomUUID()), new CommunityId(UUID.randomUUID()),
                EventType.WORD, new EventTitle("Opening word"), LocalDate.now().plusDays(10),
                new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0)), new RoomId(UUID.randomUUID()),
                new BookingRequestId(UUID.randomUUID()), creator, Instant.now()));
    }

    @Test
    void deletingAUserClearsTheCreatorAndKeepsTheEvent() {
        Event event = storedBy(deleted);

        transaction.executeWithoutResult(status -> publisher.publishEvent(new UserDeleted(deleted.value())));

        assertThat(jdbc.queryForList("select created_by from event", UUID.class)).containsOnlyNulls();
        Event found = events.findById(event.id()).orElseThrow();
        assertThat(found.createdBy()).isEmpty();
        assertThat(found.title()).isEqualTo(new EventTitle("Opening word"));
    }

    @Test
    void eventsOfOtherUsersKeepTheirCreator() {
        Event kept = storedBy(other);
        storedBy(deleted);

        transaction.executeWithoutResult(status -> publisher.publishEvent(new UserDeleted(deleted.value())));

        assertThat(events.findById(kept.id()).orElseThrow().createdBy()).contains(other);
    }

    @Test
    void aFailureAnonymizingRollsBackTheTransactionOfTheDeletion() {
        Event event = storedBy(deleted);
        doThrow(new IllegalStateException("database unavailable")).when(events).anonymize(any());

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("update event set title = 'Deleted with the user' where id = ?", event.id().value());
            publisher.publishEvent(new UserDeleted(deleted.value()));
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select title from event", String.class)).isEqualTo("Opening word");
        assertThat(events.findById(event.id()).orElseThrow().createdBy()).contains(deleted);
    }
}
