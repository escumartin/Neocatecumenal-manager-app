package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public static Set<BookingStatus> activeStatuses() {
        return EnumSet.of(PENDING, APPROVED);
    }

    public boolean active() {
        return this == PENDING || this == APPROVED;
    }
}
