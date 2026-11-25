package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import java.time.LocalTime;
import java.util.UUID;

public record OccupancyRow(LocalTime startTime, LocalTime endTime, BookingStatus status, UUID communityId) {
}
