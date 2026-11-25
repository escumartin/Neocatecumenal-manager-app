package io.github.escumartin.neocatecumenal.spaces.application.port.in;

import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;

public interface DeactivateRoom {

    void deactivate(Actor actor, RoomId roomId);
}
