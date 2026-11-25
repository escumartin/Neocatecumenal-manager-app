package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.PurgeExpiredBookings;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.StatusChange;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
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
class PurgeIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private BookingRequestRepository requests;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private PurgeExpiredBookings purge;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduler;

    @Autowired
    private JdbcTemplate jdbc;

    private Room room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY),
                new RoomName("Main hall")));
    }

    private BookingRequest seriesEndingOn(LocalDate lastDate) {
        List<Booking> occurrences = List.of(
                new Booking(new BookingId(UUID.randomUUID()), lastDate.minusWeeks(1), SLOT, BookingStatus.APPROVED),
                new Booking(new BookingId(UUID.randomUUID()), lastDate, SLOT, BookingStatus.CANCELLED));
        UserId user = new UserId(UUID.randomUUID());
        List<StatusChange> history = List.of(
                new StatusChange(null, null, BookingRequestStatus.PENDING, user, Instant.now()),
                new StatusChange(occurrences.get(1).id(), null, BookingRequestStatus.CANCELLED, user, Instant.now()));
        return requests.save(BookingRequest.restore(new BookingRequestId(UUID.randomUUID()), room.id(), COMMUNITY,
                user, new Reason("Catechesis"), null, lastDate.minusWeeks(1), lastDate, SLOT,
                BookingRequestStatus.APPROVED, occurrences, history));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void aRequestEndedOneYearAndADayAgoIsDeletedWithOccurrencesAndHistory() {
        BookingRequest expired = seriesEndingOn(TODAY.minusYears(1).minusDays(1));

        purge.purge();

        assertThat(requests.findById(expired.id())).isEmpty();
        assertThat(count("booking_request")).isZero();
        assertThat(count("booking")).isZero();
        assertThat(count("booking_status_change")).isZero();
    }

    @Test
    void aRequestEndedExactlyOneYearAgoIsKeptWithEverything() {
        BookingRequest boundary = seriesEndingOn(TODAY.minusYears(1));

        purge.purge();

        assertThat(requests.findById(boundary.id())).isPresent();
        assertThat(count("booking")).isEqualTo(2);
        assertThat(count("booking_status_change")).isEqualTo(2);
    }

    @Test
    void onlyTheExpiredRequestsAreDeleted() {
        seriesEndingOn(TODAY.minusYears(2));
        BookingRequest recent = seriesEndingOn(TODAY.minusDays(5));
        BookingRequest upcoming = seriesEndingOn(TODAY.plusDays(20));

        purge.purge();

        assertThat(count("booking_request")).isEqualTo(2);
        assertThat(requests.findById(recent.id())).isPresent();
        assertThat(requests.findById(upcoming.id())).isPresent();
        assertThat(count("booking")).isEqualTo(4);
    }

    @Test
    void thePurgeJobIsScheduledByTheApplication() {
        List<String> scheduled = scheduler.getScheduledTasks().stream()
                .map(task -> task.getTask().getRunnable().toString())
                .toList();

        assertThat(scheduled).anyMatch(runnable -> runnable.contains("BookingPurgeJob"));
    }
}
