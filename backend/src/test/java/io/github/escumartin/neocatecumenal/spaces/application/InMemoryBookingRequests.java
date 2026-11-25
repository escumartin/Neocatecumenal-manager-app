package io.github.escumartin.neocatecumenal.spaces.application;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.StatusChange;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class InMemoryBookingRequests implements BookingRequestRepository {

    final Map<BookingRequestId, BookingRequest> stored = new LinkedHashMap<>();

    @Override
    public BookingRequest save(BookingRequest request) {
        stored.put(request.id(), copy(request));
        return request;
    }

    @Override
    public Optional<BookingRequest> findById(BookingRequestId id) {
        return Optional.ofNullable(stored.get(id)).map(InMemoryBookingRequests::copy);
    }

    @Override
    public List<Conflict> findConflicts(RoomId roomId, Collection<LocalDate> dates, TimeSlot slot) {
        return stored.values().stream()
                .filter(request -> request.roomId().equals(roomId))
                .flatMap(request -> request.bookings().stream())
                .filter(booking -> booking.status().active())
                .filter(booking -> dates.contains(booking.date()))
                .filter(booking -> booking.slot().overlaps(slot))
                .map(booking -> new Conflict(booking.date(), booking.slot()))
                .sorted(Comparator.comparing(Conflict::date).thenComparing(conflict -> conflict.slot().start()))
                .toList();
    }

    @Override
    public BookingRequestPage search(Optional<CommunityId> communityId, Set<BookingRequestStatus> statuses, int page,
            int size) {
        List<BookingRequest> matching = stored.values().stream()
                .filter(request -> communityId.map(request.communityId()::equals).orElse(true))
                .filter(request -> statuses.isEmpty() || statuses.contains(request.status()))
                .sorted(Comparator.comparing(BookingRequest::firstDate))
                .map(InMemoryBookingRequests::copy)
                .toList();
        List<BookingRequest> items = matching.stream().skip((long) page * size).limit(size).toList();
        return new BookingRequestPage(items, page, size, matching.size());
    }

    @Override
    public boolean existsByCommunity(CommunityId communityId) {
        return stored.values().stream().anyMatch(request -> request.communityId().equals(communityId));
    }

    @Override
    public int anonymize(UserId userId) {
        int affected = 0;
        for (BookingRequest request : List.copyOf(stored.values())) {
            boolean requester = request.requestedBy().filter(userId::equals).isPresent();
            boolean changer = request.history().stream().anyMatch(change -> userId.equals(change.changedBy()));
            if (requester || changer) {
                affected++;
                stored.put(request.id(), BookingRequest.restore(request.id(), request.roomId(),
                        request.communityId(), requester ? null : request.requestedBy().orElse(null),
                        request.reason(), request.rejectionReason().orElse(null), request.firstDate(),
                        request.lastDate(), request.slot(), request.recurrence().orElse(null),
                        request.eventId().orElse(null), request.status(), request.bookings(),
                        request.history().stream()
                                .map(change -> userId.equals(change.changedBy())
                                        ? new StatusChange(change.bookingId(), change.from(), change.to(), null,
                                                change.changedAt())
                                        : change)
                                .toList()));
            }
        }
        return affected;
    }

    @Override
    public int deleteWithLastDateBefore(LocalDate date) {
        int before = stored.size();
        stored.values().removeIf(request -> request.lastDate().isBefore(date));
        return before - stored.size();
    }

    private static BookingRequest copy(BookingRequest request) {
        return BookingRequest.restore(request.id(), request.roomId(), request.communityId(),
                request.requestedBy().orElse(null), request.reason(),
                request.rejectionReason().orElse(null), request.firstDate(), request.lastDate(), request.slot(),
                request.recurrence().orElse(null), request.eventId().orElse(null), request.status(), request.bookings(), request.history());
    }
}
