package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class SeriesPersistenceIT {

    private static final LocalDate FIRST = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(2);
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
    private RequestBooking requestBooking;

    @Autowired
    private JdbcTemplate jdbc;

    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY);

    private Room room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    private BookingRequest requestSeries(TimeSlot slot, LocalDate until) {
        return requestBooking.request(moderator, room.id(), null, FIRST, slot, new Reason("Catechesis"),
                new WeeklyRecurrence(until));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void eightWeeksWithoutConflictsStoreEightOccurrences() {
        BookingRequest created = requestSeries(SLOT, FIRST.plusWeeks(7));

        BookingRequest found = requests.findById(created.id()).orElseThrow();

        assertThat(found.bookings()).hasSize(8);
        assertThat(found.bookings()).extracting(Booking::date)
                .containsExactlyElementsOf(created.bookings().stream().map(Booking::date).toList());
        assertThat(found.lastDate()).isEqualTo(FIRST.plusWeeks(7));
        assertThat(found.recurrence()).contains(new WeeklyRecurrence(FIRST.plusWeeks(7)));
        assertThat(count("booking")).isEqualTo(8);
    }

    @Test
    void theRecurrenceUntilIsPersistedAndASimpleRequestKeepsItNull() {
        requestSeries(SLOT, FIRST.plusWeeks(2));
        requestBooking.request(moderator, room.id(), null, FIRST, new TimeSlot(LocalTime.of(8, 0),
                LocalTime.of(9, 0)), new Reason("Single"));

        assertThat(jdbc.queryForList("select recurrence_until from booking_request where reason = 'Catechesis'",
                LocalDate.class)).containsExactly(FIRST.plusWeeks(2));
        assertThat(count("booking_request where reason = 'Single' and recurrence_until is null")).isEqualTo(1);
    }

    @Test
    void aConflictingOccurrenceStoresNoOccurrenceAtAll() {
        requestBooking.request(moderator, room.id(), null, FIRST.plusWeeks(4), SLOT, new Reason("Rehearsal"));

        assertThatThrownBy(() -> requestSeries(SLOT, FIRST.plusWeeks(7)))
                .isInstanceOfSatisfying(RoomOccupied.class, failure -> assertThat(failure.conflicts())
                        .containsExactly(new Conflict(FIRST.plusWeeks(4), SLOT)));
        assertThat(count("booking_request")).isEqualTo(1);
        assertThat(count("booking")).isEqualTo(1);
    }

    @Test
    void anEndDateExactlyOneYearAwayStoresFiftyThreeOccurrences() {
        BookingRequest created = requestSeries(SLOT, FIRST.plusYears(1));

        assertThat(requests.findById(created.id()).orElseThrow().bookings()).hasSize(53);
        assertThat(count("booking")).isEqualTo(53);
    }

    @Test
    void anEndDateBeyondOneYearStoresNothing() {
        assertThatThrownBy(() -> requestSeries(SLOT, FIRST.plusYears(1).plusDays(1)))
                .isInstanceOf(SpacesFieldInvalid.class);
        assertThat(count("booking_request")).isZero();
        assertThat(count("booking")).isZero();
    }
}
