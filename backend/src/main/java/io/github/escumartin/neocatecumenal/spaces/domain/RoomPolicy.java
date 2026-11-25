package io.github.escumartin.neocatecumenal.spaces.domain;

public final class RoomPolicy {

    private RoomPolicy() {
    }

    public static void requireAdministrator(Actor actor) {
        if (actor.role() != ActorRole.ADMINISTRATOR) {
            throw new SpacesForbidden();
        }
    }
}
