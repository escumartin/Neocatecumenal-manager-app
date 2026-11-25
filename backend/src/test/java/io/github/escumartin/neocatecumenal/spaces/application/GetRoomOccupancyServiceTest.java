package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.RoomServicesTest.InMemoryRooms;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetRoomOccupancy.OccupiedSlot;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetRoomOccupancy.RoomOccupancy;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.OccupiedBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomOccupancyQuery;
import io.github.escumartin.neocatecumenal.spaces.application.service.GetRoomOccupancyService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetRoomOccupancyServiceTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 11);
    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());
    private static final TimeSlot MORNING = new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0));
    private static final TimeSlot EVENING = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));

    private final InMemoryRooms rooms = new InMemoryRooms();
    private final List<OccupiedBooking> occupied = new ArrayList<>();
    private final List<RoomId> queriedRooms = new ArrayList<>();
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, OWN);
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, OWN);
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, OWN);

    private GetRoomOccupancyService service;
    private Room room;

    @BeforeEach
    void setUp() {
        RoomOccupancyQuery query = (roomId, date) -> {
            queriedRooms.add(roomId);
            return occupied;
        };
        service = new GetRoomOccupancyService(rooms, query);
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    @Test
    void anEmptyDayHasNoOccupiedSlots() {
        RoomOccupancy result = service.get(moderator, room.id(), DAY, null);

        assertThat(result.roomId()).isEqualTo(room.id());
        assertThat(result.date()).isEqualTo(DAY);
        assertThat(result.occupied()).isEmpty();
    }

    @Test
    void marksOwnCommunitySlotsForAModeratorAndKeepsTheOrderOfTheQuery() {
        occupied.add(new OccupiedBooking(MORNING, BookingStatus.APPROVED, OTHER));
        occupied.add(new OccupiedBooking(EVENING, BookingStatus.PENDING, OWN));

        RoomOccupancy result = service.get(moderator, room.id(), DAY, null);

        assertThat(result.occupied()).containsExactly(new OccupiedSlot(MORNING, BookingStatus.APPROVED, false),
                new OccupiedSlot(EVENING, BookingStatus.PENDING, true));
    }

    @Test
    void anAdministratorGetsOwnCommunityRelativeToTheChosenCommunity() {
        occupied.add(new OccupiedBooking(MORNING, BookingStatus.APPROVED, OTHER));
        occupied.add(new OccupiedBooking(EVENING, BookingStatus.PENDING, OWN));

        RoomOccupancy result = service.get(administrator, room.id(), DAY, OTHER);

        assertThat(result.occupied()).extracting(OccupiedSlot::ownCommunity).containsExactly(true, false);
    }

    @Test
    void anAdministratorMustChooseACommunity() {
        assertThatThrownBy(() -> service.get(administrator, room.id(), DAY, null))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class,
                        failure -> assertThat(failure.code()).isEqualTo("COMMUNITY_REQUIRED"));
        assertThat(queriedRooms).isEmpty();
    }

    @Test
    void aModeratorCannotAskForAnotherCommunity() {
        assertThatThrownBy(() -> service.get(moderator, room.id(), DAY, OTHER)).isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void aRegisteredUserIsForbidden() {
        assertThatThrownBy(() -> service.get(registered, room.id(), DAY, null)).isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void anUnknownRoomIsNotFound() {
        assertThatThrownBy(() -> service.get(moderator, new RoomId(UUID.randomUUID()), DAY, null))
                .isInstanceOf(RoomNotFound.class);
    }

    @Test
    void anInactiveRoomIsNotFound() {
        rooms.save(Room.restore(room.id(), room.name(), false));

        assertThatThrownBy(() -> service.get(moderator, room.id(), DAY, null)).isInstanceOf(RoomNotFound.class);
        assertThat(queriedRooms).isEmpty();
    }
}
