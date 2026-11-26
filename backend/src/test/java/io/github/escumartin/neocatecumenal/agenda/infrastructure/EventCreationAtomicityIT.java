package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.CreateEvent;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.EventDetails;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.ActorRole;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomId;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class EventCreationAtomicityIT {

    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private CreateEvent createEvent;

    @MockitoSpyBean
    private EventRepository events;

    @Autowired
    private JdbcTemplate jdbc;

    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY);
    private final LocalDate tomorrow = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(1);

    private RoomId room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
        jdbc.update("insert into identity_community (id, number, active) values (?, 1, true) "
                + "on conflict (id) do nothing", COMMUNITY.value());
        room = new RoomId(UUID.randomUUID());
        jdbc.update("insert into room (id, name, active, version, created_at) values (?, 'Main hall', true, 0, now())",
                room.value());
    }

    private EventDetails create(RoomId roomId, TimeSlot slot) {
        return createEvent.create(moderator, null, EventType.WORD, new EventTitle("Opening word"), tomorrow, slot,
                roomId);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void aFreeRoomSavesTheEventAndItsPendingRequestLinkedToIt() {
        EventDetails created = create(room, SLOT);

        assertThat(count("event")).isEqualTo(1);
        assertThat(count("booking_request")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from booking_request", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select event_id from booking_request", UUID.class))
                .isEqualTo(created.event().id().value());
        assertThat(jdbc.queryForObject("select booking_request_id from event", UUID.class))
                .isEqualTo(created.event().bookingRequestId().value());
        assertThat(jdbc.queryForObject("select reason from booking_request", String.class))
                .isEqualTo("Opening word");
    }

    @Test
    void anOccupiedRoomLeavesNoSecondEventNorRequest() {
        create(room, SLOT);

        assertThatThrownBy(() -> create(room,
                new TimeSlot(LocalTime.of(19, 0), LocalTime.of(21, 0)))).isInstanceOf(RoomOccupied.class);

        assertThat(count("event")).isEqualTo(1);
        assertThat(count("booking_request")).isEqualTo(1);
        assertThat(count("booking")).isEqualTo(1);
    }

    @Test
    void anInactiveRoomLeavesNeitherEventNorRequest() {
        jdbc.update("update room set active = false where id = ?", room.value());

        assertThatThrownBy(() -> create(room, SLOT)).isInstanceOf(RoomInactive.class);

        assertThat(count("event")).isZero();
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anUnknownRoomLeavesNeitherEventNorRequest() {
        assertThatThrownBy(() -> create(new RoomId(UUID.randomUUID()), SLOT)).isInstanceOf(RoomNotFound.class);

        assertThat(count("event")).isZero();
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aFailureSavingTheEventRollsBackTheBookingRequest() {
        doThrow(new IllegalStateException("event store down")).when(events).save(any());

        assertThatThrownBy(() -> create(room, SLOT))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("event")).isZero();
        assertThat(count("booking_request")).isZero();
        assertThat(count("booking")).isZero();
    }
}
