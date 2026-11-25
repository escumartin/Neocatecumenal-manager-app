package io.github.escumartin.neocatecumenal.spaces.domain;

public class RoomHasActiveBookings extends RuntimeException {

    private final int count;

    public RoomHasActiveBookings(int count) {
        this.count = count;
    }

    public int count() {
        return count;
    }
}
