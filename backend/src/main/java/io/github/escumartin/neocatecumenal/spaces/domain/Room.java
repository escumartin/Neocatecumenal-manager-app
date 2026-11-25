package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.Objects;
import java.util.UUID;

public final class Room {

    private final RoomId id;
    private RoomName name;
    private boolean active;

    private Room(RoomId id, RoomName name, boolean active) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.active = active;
    }

    public static Room create(Actor actor, RoomName name) {
        RoomPolicy.requireAdministrator(actor);
        return new Room(new RoomId(UUID.randomUUID()), name, true);
    }

    public static Room restore(RoomId id, RoomName name, boolean active) {
        return new Room(id, name, active);
    }

    public void rename(Actor actor, RoomName newName) {
        RoomPolicy.requireAdministrator(actor);
        requireActive();
        this.name = Objects.requireNonNull(newName);
    }

    public void deactivate(Actor actor, int activeFutureBookings) {
        RoomPolicy.requireAdministrator(actor);
        requireActive();
        if (activeFutureBookings > 0) {
            throw new RoomHasActiveBookings(activeFutureBookings);
        }
        this.active = false;
    }

    public void requireActive() {
        if (!active) {
            throw new RoomInactive();
        }
    }

    public RoomId id() {
        return id;
    }

    public RoomName name() {
        return name;
    }

    public boolean active() {
        return active;
    }
}
