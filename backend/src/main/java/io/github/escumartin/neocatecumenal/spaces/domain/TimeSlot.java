package io.github.escumartin.neocatecumenal.spaces.domain;

import java.time.LocalTime;
import java.util.Objects;

public record TimeSlot(LocalTime start, LocalTime end) {

    public TimeSlot {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        requireMinutePrecision("startTime", start);
        requireMinutePrecision("endTime", end);
        if (!end.isAfter(start)) {
            throw new SpacesFieldInvalid("endTime", "END_NOT_AFTER_START");
        }
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    private static void requireMinutePrecision(String field, LocalTime time) {
        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw new SpacesFieldInvalid(field, "INVALID_FORMAT");
        }
    }
}
