package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.EventId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RejectionReason;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.StatusChange;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence;

final class BookingRequestPersistenceMapper {

    private BookingRequestPersistenceMapper() {
    }

    static BookingRequestEntity newEntity(BookingRequest request) {
        BookingRequestEntity entity = new BookingRequestEntity();
        entity.id = request.id().value();
        entity.roomId = request.roomId().value();
        entity.communityId = request.communityId().value();
        entity.requestedBy = request.requestedBy().map(UserId::value).orElse(null);
        entity.eventId = request.eventId().map(EventId::value).orElse(null);
        entity.firstDate = request.firstDate();
        entity.lastDate = request.lastDate();
        entity.recurrenceUntil = request.recurrence().map(WeeklyRecurrence::until).orElse(null);
        entity.startTime = request.slot().start();
        entity.endTime = request.slot().end();
        entity.createdAt = request.createdAt();
        request.bookings().forEach(booking -> entity.bookings.add(toEntity(request, booking)));
        return entity;
    }

    static void copyChanges(BookingRequest request, BookingRequestEntity entity) {
        entity.reason = request.reason().value();
        entity.rejectionReason = request.rejectionReason().map(RejectionReason::value).orElse(null);
        entity.status = request.status();
        for (Booking booking : request.bookings()) {
            entity.bookings.stream().filter(existing -> existing.id.equals(booking.id().value()))
                    .forEach(existing -> existing.status = booking.status());
        }
        request.history().stream().skip(entity.history.size())
                .forEach(change -> entity.history.add(toEntity(change)));
    }

    static BookingRequest toDomain(BookingRequestEntity entity) {
        return BookingRequest.restore(new BookingRequestId(entity.id), new RoomId(entity.roomId),
                new CommunityId(entity.communityId),
                entity.requestedBy == null ? null : new UserId(entity.requestedBy), new Reason(entity.reason),
                entity.rejectionReason == null ? null : new RejectionReason(entity.rejectionReason),
                entity.firstDate, entity.lastDate, new TimeSlot(entity.startTime, entity.endTime),
                entity.recurrenceUntil == null ? null : new WeeklyRecurrence(entity.recurrenceUntil),
                entity.eventId == null ? null : new EventId(entity.eventId), entity.status,
                entity.bookings.stream().map(BookingRequestPersistenceMapper::toDomain).toList(),
                entity.history.stream().map(BookingRequestPersistenceMapper::toDomain).toList());
    }

    private static BookingEntity toEntity(BookingRequest request, Booking booking) {
        BookingEntity entity = new BookingEntity();
        entity.id = booking.id().value();
        entity.roomId = request.roomId().value();
        entity.bookingDate = booking.date();
        entity.startTime = booking.slot().start();
        entity.endTime = booking.slot().end();
        entity.status = booking.status();
        return entity;
    }

    private static BookingStatusChangeEntity toEntity(StatusChange change) {
        BookingStatusChangeEntity entity = new BookingStatusChangeEntity();
        entity.bookingId = change.bookingId() == null ? null : change.bookingId().value();
        entity.fromStatus = change.from();
        entity.toStatus = change.to();
        entity.changedBy = change.changedBy() == null ? null : change.changedBy().value();
        entity.changedAt = change.changedAt();
        return entity;
    }

    private static Booking toDomain(BookingEntity entity) {
        return new Booking(new BookingId(entity.id), entity.bookingDate,
                new TimeSlot(entity.startTime, entity.endTime), entity.status);
    }

    private static StatusChange toDomain(BookingStatusChangeEntity entity) {
        return new StatusChange(entity.bookingId == null ? null : new BookingId(entity.bookingId), entity.fromStatus,
                entity.toStatus, entity.changedBy == null ? null : new UserId(entity.changedBy), entity.changedAt);
    }
}
