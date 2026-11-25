package io.github.escumartin.neocatecumenal.spaces.domain;

import java.time.LocalDate;

public record Conflict(LocalDate date, TimeSlot slot) {
}
