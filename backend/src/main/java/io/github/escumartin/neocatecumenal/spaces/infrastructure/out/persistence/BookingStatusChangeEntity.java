package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_status_change")
class BookingStatusChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "booking_id")
    UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    BookingRequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    BookingRequestStatus toStatus;

    @Column(name = "changed_by")
    UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    Instant changedAt;

    protected BookingStatusChangeEntity() {
    }
}
