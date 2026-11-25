package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.model.Booking;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequest;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequestDetail;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequestPage;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.generated.model.BookingStatus;
import io.github.escumartin.neocatecumenal.generated.model.RejectBookingRequest;
import io.github.escumartin.neocatecumenal.generated.model.StatusChange;
import io.github.escumartin.neocatecumenal.generated.model.WeeklyRecurrence;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RejectionReason;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

final class BookingRequestsMapper {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private BookingRequestsMapper() {
    }

    static Reason toReason(String reason) {
        return new Reason(reason);
    }

    static RejectionReason toRejectionReason(RejectBookingRequest body) {
        return body == null || body.getReason() == null ? null : new RejectionReason(body.getReason());
    }

    static io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence toRecurrence(
            WeeklyRecurrence recurrence) {
        return recurrence == null ? null
                : new io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence(recurrence.getUntil());
    }

    static TimeSlot toSlot(String startTime, String endTime) {
        return new TimeSlot(LocalTime.parse(startTime), LocalTime.parse(endTime));
    }

    static Set<io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus> toStatuses(
            Collection<BookingRequestStatus> statuses) {
        Set<io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus> result = EnumSet
                .noneOf(io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus.class);
        if (statuses != null) {
            statuses.forEach(status -> result.add(
                    io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus
                            .valueOf(status.getValue())));
        }
        return result;
    }

    static BookingRequest toResponse(io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest request) {
        BookingRequest response = new BookingRequest(request.id().value(), request.roomId().value(),
                request.communityId().value(), request.firstDate(), time(request.slot().start()),
                time(request.slot().end()), request.reason().value(),
                BookingRequestStatus.fromValue(request.status().name()), request.bookings().size(),
                OffsetDateTime.ofInstant(request.createdAt(), ZoneOffset.UTC));
        request.rejectionReason().ifPresent(reason -> response.setRejectionReason(reason.value()));
        request.recurrence().ifPresent(recurrence -> response.setRecurrence(toRecurrence(recurrence)));
        return response;
    }

    static BookingRequestDetail toDetail(io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest request) {
        BookingRequestDetail response = new BookingRequestDetail(request.id().value(), request.roomId().value(),
                request.communityId().value(), request.firstDate(), time(request.slot().start()),
                time(request.slot().end()), request.reason().value(),
                BookingRequestStatus.fromValue(request.status().name()), request.bookings().size(),
                OffsetDateTime.ofInstant(request.createdAt(), ZoneOffset.UTC),
                request.bookings().stream().map(BookingRequestsMapper::toBooking).toList(),
                request.history().stream().map(BookingRequestsMapper::toChange).toList());
        request.rejectionReason().ifPresent(reason -> response.setRejectionReason(reason.value()));
        request.recurrence().ifPresent(recurrence -> response.setRecurrence(toRecurrence(recurrence)));
        return response;
    }

    static BookingRequestPage toPage(
            io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage page) {
        return new BookingRequestPage(page.items().stream().map(BookingRequestsMapper::toResponse).toList(),
                page.page(), page.size(), Math.toIntExact(page.totalItems()));
    }

    private static WeeklyRecurrence toRecurrence(
            io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence recurrence) {
        return new WeeklyRecurrence(WeeklyRecurrence.FrequencyEnum.WEEKLY, recurrence.until());
    }

    private static Booking toBooking(io.github.escumartin.neocatecumenal.spaces.domain.Booking booking) {
        return new Booking(booking.id().value(), booking.date(), time(booking.slot().start()),
                time(booking.slot().end()), BookingStatus.fromValue(booking.status().name()));
    }

    private static StatusChange toChange(io.github.escumartin.neocatecumenal.spaces.domain.StatusChange change) {
        StatusChange response = new StatusChange(StatusChange.ToStatusEnum.fromValue(change.to().name()),
                OffsetDateTime.ofInstant(change.changedAt(), ZoneOffset.UTC));
        if (change.bookingId() != null) {
            response.setBookingId(change.bookingId().value());
        }
        if (change.from() != null) {
            response.setFromStatus(StatusChange.FromStatusEnum.fromValue(change.from().name()));
        }
        if (change.changedBy() != null) {
            response.setChangedBy(change.changedBy().value());
        }
        return response;
    }

    private static String time(LocalTime time) {
        return TIME.format(time);
    }
}
