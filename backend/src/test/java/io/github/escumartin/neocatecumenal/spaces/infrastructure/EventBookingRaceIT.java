package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Booked;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Occupied;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class EventBookingRaceIT {

    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private SpacesApi spacesApi;

    @Autowired
    private RoomRepository rooms;

    @MockitoSpyBean
    private BookingRequestRepository requests;

    @Autowired
    private JdbcTemplate jdbc;

    private Room room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY),
                new RoomName("Main hall")));
    }

    private EventBookingCommand command() {
        return new EventBookingCommand(UUID.randomUUID(), room.id().value(), COMMUNITY.value(), UUID.randomUUID(),
                LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(7), LocalTime.of(18, 0), LocalTime.of(20, 0),
                "Opening word");
    }

    @Test
    void aSlotLostAtTheDatabaseWithoutAnOuterTransactionIsReportedAsOccupied() {
        doReturn(List.of()).when(requests).findConflicts(any(), any(), any());
        EventBookingResult first = spacesApi.requestForEvent(command());

        EventBookingResult second = spacesApi.requestForEvent(command());

        assertThat(first).isInstanceOf(Booked.class);
        assertThat(second).isInstanceOf(Occupied.class);
        assertThat(jdbc.queryForObject("select count(*) from booking_request", Integer.class)).isEqualTo(1);
    }
}
