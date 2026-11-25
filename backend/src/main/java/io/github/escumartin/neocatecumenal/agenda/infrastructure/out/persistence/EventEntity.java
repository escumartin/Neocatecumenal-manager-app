package io.github.escumartin.neocatecumenal.agenda.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.agenda.domain.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "event")
class EventEntity {

    @Id
    UUID id;

    @Column(name = "community_id", nullable = false)
    UUID communityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    EventType type;

    @Column(nullable = false, length = 120)
    String title;

    @Column(name = "event_date", nullable = false)
    LocalDate eventDate;

    @Column(name = "start_time", nullable = false)
    LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    LocalTime endTime;

    @Column(name = "room_id", nullable = false)
    UUID roomId;

    @Column(name = "booking_request_id", nullable = false)
    UUID bookingRequestId;

    @Column(name = "created_by")
    UUID createdBy;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected EventEntity() {
    }
}
