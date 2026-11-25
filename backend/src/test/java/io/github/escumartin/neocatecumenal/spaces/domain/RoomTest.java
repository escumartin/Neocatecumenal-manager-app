package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RoomTest {

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, new CommunityId(UUID.randomUUID()));
    }

    private static Room activeRoom() {
        return Room.create(actor(ActorRole.ADMINISTRATOR), new RoomName("Main hall"));
    }

    @Test
    void administratorCreatesAnActiveRoom() {
        Room room = Room.create(actor(ActorRole.ADMINISTRATOR), new RoomName("Main hall"));

        assertThat(room.id()).isNotNull();
        assertThat(room.name()).isEqualTo(new RoomName("Main hall"));
        assertThat(room.active()).isTrue();
    }

    @Test
    void everyCreatedRoomGetsItsOwnId() {
        assertThat(activeRoom().id()).isNotEqualTo(activeRoom().id());
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MODERATOR", "REGISTERED"})
    void nonAdministratorsCannotCreateRooms(ActorRole role) {
        assertThatThrownBy(() -> Room.create(actor(role), new RoomName("Main hall")))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void restoredRoomKeepsItsState() {
        RoomId id = new RoomId(UUID.randomUUID());

        Room room = Room.restore(id, new RoomName("Old hall"), false);

        assertThat(room.id()).isEqualTo(id);
        assertThat(room.name()).isEqualTo(new RoomName("Old hall"));
        assertThat(room.active()).isFalse();
    }

    @Test
    void administratorRenamesAnActiveRoom() {
        Room room = activeRoom();

        room.rename(actor(ActorRole.ADMINISTRATOR), new RoomName("Side hall"));

        assertThat(room.name()).isEqualTo(new RoomName("Side hall"));
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MODERATOR", "REGISTERED"})
    void nonAdministratorsCannotRename(ActorRole role) {
        Room room = activeRoom();

        assertThatThrownBy(() -> room.rename(actor(role), new RoomName("Side hall")))
                .isInstanceOf(SpacesForbidden.class);
        assertThat(room.name()).isEqualTo(new RoomName("Main hall"));
    }

    @Test
    void cannotRenameAnInactiveRoom() {
        Room room = activeRoom();
        room.deactivate(actor(ActorRole.ADMINISTRATOR), 0);

        assertThatThrownBy(() -> room.rename(actor(ActorRole.ADMINISTRATOR), new RoomName("Side hall")))
                .isInstanceOf(RoomInactive.class);
        assertThat(room.name()).isEqualTo(new RoomName("Main hall"));
    }

    @Test
    void deactivatesWhenThereAreNoActiveFutureBookings() {
        Room room = activeRoom();

        room.deactivate(actor(ActorRole.ADMINISTRATOR), 0);

        assertThat(room.active()).isFalse();
    }

    @Test
    void refusesToDeactivateWithActiveFutureBookings() {
        Room room = activeRoom();

        assertThatThrownBy(() -> room.deactivate(actor(ActorRole.ADMINISTRATOR), 3))
                .isInstanceOfSatisfying(RoomHasActiveBookings.class, failure -> assertThat(failure.count()).isEqualTo(3));
        assertThat(room.active()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MODERATOR", "REGISTERED"})
    void nonAdministratorsCannotDeactivate(ActorRole role) {
        Room room = activeRoom();

        assertThatThrownBy(() -> room.deactivate(actor(role), 0)).isInstanceOf(SpacesForbidden.class);
        assertThat(room.active()).isTrue();
    }

    @Test
    void deactivationIsPermanent() {
        Room room = activeRoom();
        room.deactivate(actor(ActorRole.ADMINISTRATOR), 0);

        assertThatThrownBy(() -> room.deactivate(actor(ActorRole.ADMINISTRATOR), 0))
                .isInstanceOf(RoomInactive.class);
        assertThat(room.active()).isFalse();
    }

    @Test
    void authorizationIsCheckedBeforeTheState() {
        Room room = activeRoom();
        room.deactivate(actor(ActorRole.ADMINISTRATOR), 0);

        assertThatThrownBy(() -> room.deactivate(actor(ActorRole.MODERATOR), 0)).isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void policyRequiresAnAdministrator() {
        RoomPolicy.requireAdministrator(actor(ActorRole.ADMINISTRATOR));

        assertThatThrownBy(() -> RoomPolicy.requireAdministrator(actor(ActorRole.MODERATOR)))
                .isInstanceOf(SpacesForbidden.class);
    }
}
