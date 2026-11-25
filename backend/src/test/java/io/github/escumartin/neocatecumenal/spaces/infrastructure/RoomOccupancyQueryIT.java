package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.OccupiedBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomOccupancyQuery;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
@Import(TestCommunityProvisionerConfig.class)
class RoomOccupancyQueryIT {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2030, 5, 11);
    private static final CommunityId COMMUNITY_A = new CommunityId(UUID.randomUUID());
    private static final CommunityId COMMUNITY_B = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private RoomOccupancyQuery query;

    @Autowired
    private BookingRequestRepository requests;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private JdbcTemplate jdbc;

    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
            COMMUNITY_A);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_A);

    private Room room;

    private static TimeSlot slot(int startHour, int endHour) {
        return new TimeSlot(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    private BookingRequest save(Room target, CommunityId community, LocalDate date, TimeSlot slot) {
        return requests.save(BookingRequest.request(moderator, target.id(), community, date, slot,
                new Reason("Secret reason"), NOW, AT));
    }

    @Test
    void anEmptyDayHasNoOccupiedSlots() {
        assertThat(query.occupiedSlots(room.id(), DAY)).isEmpty();
    }

    @Test
    void returnsPendingAndApprovedSlotsOrderedByStartWithTheirCommunity() {
        save(room, COMMUNITY_B, DAY, slot(18, 20));
        BookingRequest approved = save(room, COMMUNITY_A, DAY, slot(9, 10));
        jdbc.update("update booking set status = 'APPROVED' where booking_request_id = ?", approved.id().value());

        assertThat(query.occupiedSlots(room.id(), DAY)).containsExactly(
                new OccupiedBooking(slot(9, 10), BookingStatus.APPROVED, COMMUNITY_A),
                new OccupiedBooking(slot(18, 20), BookingStatus.PENDING, COMMUNITY_B));
    }

    @Test
    void ignoresRejectedAndCancelledSlots() {
        BookingRequest rejected = save(room, COMMUNITY_A, DAY, slot(9, 10));
        BookingRequest cancelled = save(room, COMMUNITY_A, DAY, slot(11, 12));
        jdbc.update("update booking set status = 'REJECTED' where booking_request_id = ?", rejected.id().value());
        jdbc.update("update booking set status = 'CANCELLED' where booking_request_id = ?", cancelled.id().value());

        assertThat(query.occupiedSlots(room.id(), DAY)).isEmpty();
    }

    @Test
    void ignoresOtherDaysAndOtherRooms() {
        Room other = rooms.save(Room.create(administrator, new RoomName("Small hall")));
        save(room, COMMUNITY_A, DAY.plusDays(1), slot(9, 10));
        save(other, COMMUNITY_A, DAY, slot(9, 10));

        assertThat(query.occupiedSlots(room.id(), DAY)).isEmpty();
    }
}
