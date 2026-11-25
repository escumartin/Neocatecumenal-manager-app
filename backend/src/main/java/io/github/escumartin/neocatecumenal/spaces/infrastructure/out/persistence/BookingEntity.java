package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "booking")
class BookingEntity {

    @Id
    UUID id;

    @Column(name = "room_id", nullable = false)
    UUID roomId;

    @Column(name = "booking_date", nullable = false)
    LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    BookingStatus status;

    protected BookingEntity() {
    }
}
