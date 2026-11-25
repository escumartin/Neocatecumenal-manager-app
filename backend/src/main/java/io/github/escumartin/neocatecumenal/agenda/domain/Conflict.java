package io.github.escumartin.neocatecumenal.agenda.domain;

import java.time.LocalDate;

public record Conflict(LocalDate date, TimeSlot slot) {
}
