package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "booking_request")
class BookingRequestEntity {

    @Id
    UUID id;

    @Column(name = "room_id", nullable = false)
    UUID roomId;

    @Column(name = "community_id", nullable = false)
    UUID communityId;

    @Column(name = "requested_by")
    UUID requestedBy;

    @Column(name = "event_id")
    UUID eventId;

    @Column(nullable = false, length = 500)
    String reason;

    @Column(name = "rejection_reason", length = 500)
    String rejectionReason;

    @Column(name = "first_date", nullable = false)
    LocalDate firstDate;

    @Column(name = "last_date", nullable = false)
    LocalDate lastDate;

    @Column(name = "recurrence_until")
    LocalDate recurrenceUntil;

    @Column(name = "start_time", nullable = false)
    LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    BookingRequestStatus status;

    @Version
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "booking_request_id", nullable = false)
    @OrderBy("bookingDate, startTime, id")
    List<BookingEntity> bookings = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "booking_request_id", nullable = false)
    @OrderBy("id")
    List<BookingStatusChangeEntity> history = new ArrayList<>();

    protected BookingRequestEntity() {
    }
}
