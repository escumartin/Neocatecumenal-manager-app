package io.github.escumartin.neocatecumenal.agenda.domain;

import java.time.LocalTime;
import java.util.Objects;

public record TimeSlot(LocalTime start, LocalTime end) {

    public TimeSlot {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        requireMinutePrecision("startTime", start);
        requireMinutePrecision("endTime", end);
        if (!end.isAfter(start)) {
            throw new AgendaFieldInvalid("endTime", "END_NOT_AFTER_START");
        }
    }

    private static void requireMinutePrecision(String field, LocalTime time) {
        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw new AgendaFieldInvalid(field, "INVALID_FORMAT");
        }
    }
}
