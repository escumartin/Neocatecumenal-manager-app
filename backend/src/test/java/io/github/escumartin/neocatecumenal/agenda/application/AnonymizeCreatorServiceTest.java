package io.github.escumartin.neocatecumenal.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.agenda.application.service.AnonymizeCreatorService;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AnonymizeCreatorServiceTest {

    private final InMemoryEvents events = new InMemoryEvents();
    private final UserId deleted = new UserId(UUID.randomUUID());
    private final UserId other = new UserId(UUID.randomUUID());

    private AnonymizeCreatorService service;

    @BeforeEach
    void setUp() {
        service = new AnonymizeCreatorService(events);
    }

    private Event storedBy(UserId creator) {
        return events.save(Event.restore(new EventId(UUID.randomUUID()), new CommunityId(UUID.randomUUID()),
                EventType.WORD, new EventTitle("Opening word"), LocalDate.of(2030, 5, 11),
                new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0)), new RoomId(UUID.randomUUID()),
                new BookingRequestId(UUID.randomUUID()), creator, Instant.parse("2030-05-01T10:00:00Z")));
    }

    @Test
    void clearsTheCreatorOfTheEventsOfTheDeletedUser() {
        Event event = storedBy(deleted);

        service.anonymize(deleted);

        assertThat(events.findById(event.id()).orElseThrow().createdBy()).isEmpty();
    }

    @Test
    void keepsTheEventItself() {
        Event event = storedBy(deleted);

        service.anonymize(deleted);

        Event found = events.findById(event.id()).orElseThrow();
        assertThat(found.title()).isEqualTo(new EventTitle("Opening word"));
        assertThat(found.date()).isEqualTo(event.date());
        assertThat(found.bookingRequestId()).isEqualTo(event.bookingRequestId());
    }

    @Test
    void leavesTheEventsOfOtherUsersUntouched() {
        Event event = storedBy(other);

        service.anonymize(deleted);

        assertThat(events.findById(event.id()).orElseThrow().createdBy()).contains(other);
    }

    @Test
    void aUserWithoutEventsChangesNothing() {
        service.anonymize(deleted);

        assertThat(events.stored).isEmpty();
    }

    @Test
    void logsOnlyTheAmountWhenSomethingIsAnonymized() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(AnonymizeCreatorService.class);
        appender.start();
        serviceLogger.addAppender(appender);
        try {
            storedBy(deleted);
            service.anonymize(deleted);
        } finally {
            serviceLogger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(pair -> pair.key)
                .containsExactly("events");
        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain(deleted.value().toString());
    }
}
