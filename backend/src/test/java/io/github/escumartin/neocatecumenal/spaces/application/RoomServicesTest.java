package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingCounter;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.application.service.CreateRoomService;
import io.github.escumartin.neocatecumenal.spaces.application.service.DeactivateRoomService;
import io.github.escumartin.neocatecumenal.spaces.application.service.ListRoomsService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RenameRoomService;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomHasActiveBookings;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNameTaken;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-10T08:30:00Z"),
            ZoneId.of("Europe/Madrid"));

    private final InMemoryRooms rooms = new InMemoryRooms();
    private final FakeCounter counter = new FakeCounter();
    private final Actor admin = actor(ActorRole.ADMINISTRATOR);
    private final Actor moderator = actor(ActorRole.MODERATOR);
    private final Actor registered = actor(ActorRole.REGISTERED);

    private CreateRoomService create;
    private RenameRoomService rename;
    private DeactivateRoomService deactivate;
    private ListRoomsService list;

    @BeforeEach
    void setUp() {
        create = new CreateRoomService(rooms);
        rename = new RenameRoomService(rooms);
        deactivate = new DeactivateRoomService(rooms, counter, CLOCK);
        list = new ListRoomsService(rooms);
    }

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, new CommunityId(UUID.randomUUID()));
    }

    private Room existing(String name) {
        return rooms.save(Room.create(admin, new RoomName(name)));
    }

    @Test
    void createsAnActiveRoomAndStoresIt() {
        Room created = create.create(admin, new RoomName("Main hall"));

        assertThat(created.active()).isTrue();
        assertThat(rooms.stored.get(created.id()).name()).isEqualTo(new RoomName("Main hall"));
    }

    @Test
    void createRejectsANameTakenByAnActiveRoomIgnoringCase() {
        existing("Main hall");

        assertThatThrownBy(() -> create.create(admin, new RoomName("MAIN HALL"))).isInstanceOf(RoomNameTaken.class);
        assertThat(rooms.stored).hasSize(1);
    }

    @Test
    void createAllowsTheNameOfAnInactiveRoom() {
        Room old = existing("Main hall");
        deactivate.deactivate(admin, old.id());

        Room created = create.create(admin, new RoomName("Main hall"));

        assertThat(rooms.stored).hasSize(2);
        assertThat(created.active()).isTrue();
    }

    @Test
    void createByANonAdministratorStoresNothing() {
        assertThatThrownBy(() -> create.create(moderator, new RoomName("Main hall")))
                .isInstanceOf(SpacesForbidden.class);
        assertThat(rooms.stored).isEmpty();
    }

    @Test
    void renamesAndStoresTheRoom() {
        Room room = existing("Main hall");

        Room renamed = rename.rename(admin, room.id(), new RoomName("Side hall"));

        assertThat(renamed.name()).isEqualTo(new RoomName("Side hall"));
        assertThat(rooms.stored.get(room.id()).name()).isEqualTo(new RoomName("Side hall"));
    }

    @Test
    void renameRejectsANameTakenByAnotherActiveRoomIgnoringCase() {
        Room room = existing("Main hall");
        existing("Side hall");

        assertThatThrownBy(() -> rename.rename(admin, room.id(), new RoomName("side HALL")))
                .isInstanceOf(RoomNameTaken.class);
        assertThat(rooms.stored.get(room.id()).name()).isEqualTo(new RoomName("Main hall"));
    }

    @Test
    void renameAllowsChangingOnlyTheCaseOfItsOwnName() {
        Room room = existing("Main hall");

        Room renamed = rename.rename(admin, room.id(), new RoomName("MAIN HALL"));

        assertThat(renamed.name()).isEqualTo(new RoomName("MAIN HALL"));
    }

    @Test
    void renameOfAnInactiveRoomFails() {
        Room room = existing("Main hall");
        deactivate.deactivate(admin, room.id());

        assertThatThrownBy(() -> rename.rename(admin, room.id(), new RoomName("Side hall")))
                .isInstanceOf(RoomInactive.class);
    }

    @Test
    void renameOfAnUnknownRoomFails() {
        assertThatThrownBy(() -> rename.rename(admin, new RoomId(UUID.randomUUID()), new RoomName("Side hall")))
                .isInstanceOf(RoomNotFound.class);
    }

    @Test
    void renameByANonAdministratorFailsBeforeLookingTheRoomUp() {
        assertThatThrownBy(() -> rename.rename(moderator, new RoomId(UUID.randomUUID()), new RoomName("Side hall")))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void deactivatesUnderALockCountingFromNowInMadrid() {
        Room room = existing("Main hall");

        deactivate.deactivate(admin, room.id());

        assertThat(rooms.stored.get(room.id()).active()).isFalse();
        assertThat(rooms.lockedForUpdate).containsExactly(room.id());
        assertThat(counter.asked).containsExactly(room.id());
        assertThat(counter.nowSeen).containsExactly(LocalDateTime.of(2030, 5, 10, 10, 30));
    }

    @Test
    void deactivateWithActiveFutureBookingsReportsTheCountAndKeepsTheRoomActive() {
        Room room = existing("Main hall");
        counter.count = 2;

        assertThatThrownBy(() -> deactivate.deactivate(admin, room.id()))
                .isInstanceOfSatisfying(RoomHasActiveBookings.class, failure -> assertThat(failure.count()).isEqualTo(2));
        assertThat(rooms.stored.get(room.id()).active()).isTrue();
    }

    @Test
    void deactivateOfAnUnknownRoomFails() {
        assertThatThrownBy(() -> deactivate.deactivate(admin, new RoomId(UUID.randomUUID())))
                .isInstanceOf(RoomNotFound.class);
    }

    @Test
    void deactivateOfAnInactiveRoomFails() {
        Room room = existing("Main hall");
        deactivate.deactivate(admin, room.id());

        assertThatThrownBy(() -> deactivate.deactivate(admin, room.id())).isInstanceOf(RoomInactive.class);
    }

    @Test
    void deactivateByANonAdministratorFailsWithoutTouchingStorage() {
        Room room = existing("Main hall");

        assertThatThrownBy(() -> deactivate.deactivate(moderator, room.id())).isInstanceOf(SpacesForbidden.class);
        assertThat(rooms.lockedForUpdate).isEmpty();
        assertThat(counter.asked).isEmpty();
        assertThat(rooms.stored.get(room.id()).active()).isTrue();
    }

    @Test
    void listReturnsOnlyActiveRooms() {
        Room kept = existing("Main hall");
        Room removed = existing("Side hall");
        deactivate.deactivate(admin, removed.id());

        assertThat(list.list()).extracting(Room::id).containsExactly(kept.id());
    }

    @Test
    void listIsEmptyWithoutRooms() {
        assertThat(list.list()).isEmpty();
    }

    @Test
    void registeredUsersCannotManageRooms() {
        Room room = existing("Main hall");

        assertThatThrownBy(() -> create.create(registered, new RoomName("Other")))
                .isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> rename.rename(registered, room.id(), new RoomName("Other")))
                .isInstanceOf(SpacesForbidden.class);
        assertThatThrownBy(() -> deactivate.deactivate(registered, room.id())).isInstanceOf(SpacesForbidden.class);
    }

    static final class InMemoryRooms implements RoomRepository {

        final Map<RoomId, Room> stored = new LinkedHashMap<>();
        final List<RoomId> lockedForUpdate = new ArrayList<>();
        final List<RoomId> lockedForShare = new ArrayList<>();

        @Override
        public Optional<Room> findById(RoomId id) {
            return Optional.ofNullable(stored.get(id)).map(InMemoryRooms::copy);
        }

        @Override
        public Optional<Room> findByIdForUpdate(RoomId id) {
            lockedForUpdate.add(id);
            return Optional.ofNullable(stored.get(id)).map(InMemoryRooms::copy);
        }

        @Override
        public Optional<Room> findByIdForShare(RoomId id) {
            lockedForShare.add(id);
            return Optional.ofNullable(stored.get(id)).map(InMemoryRooms::copy);
        }

        @Override
        public List<Room> findAllActive() {
            return stored.values().stream().filter(Room::active).map(InMemoryRooms::copy).toList();
        }

        @Override
        public boolean existsActiveByName(RoomName name) {
            return stored.values().stream().anyMatch(room -> room.active() && room.name().sameAs(name));
        }

        @Override
        public Room save(Room room) {
            stored.put(room.id(), copy(room));
            return room;
        }

        private static Room copy(Room room) {
            return Room.restore(room.id(), room.name(), room.active());
        }
    }

    static final class FakeCounter implements BookingCounter {

        int count;
        final List<RoomId> asked = new ArrayList<>();
        final List<LocalDateTime> nowSeen = new ArrayList<>();

        @Override
        public int activeFutureBookings(RoomId roomId, LocalDateTime now) {
            asked.add(roomId);
            nowSeen.add(now);
            return count;
        }
    }
}
