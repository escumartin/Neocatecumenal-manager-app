package io.github.escumartin.neocatecumenal.spaces;

import java.time.LocalDate;
import java.time.LocalTime;

public record EventBookingConflict(LocalDate date, LocalTime startTime, LocalTime endTime) {
}
