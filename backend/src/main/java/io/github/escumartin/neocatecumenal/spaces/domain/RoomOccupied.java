package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.List;

public class RoomOccupied extends RuntimeException {

    private final transient List<Conflict> conflicts;

    public RoomOccupied(List<Conflict> conflicts) {
        this.conflicts = List.copyOf(conflicts);
    }

    public List<Conflict> conflicts() {
        return conflicts;
    }
}
