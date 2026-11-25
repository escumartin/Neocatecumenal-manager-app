package io.github.escumartin.neocatecumenal.spaces.application.port.out;

import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookingRequestRepository {

    BookingRequest save(BookingRequest request);

    Optional<BookingRequest> findById(BookingRequestId id);

    List<Conflict> findConflicts(RoomId roomId, Collection<LocalDate> dates, TimeSlot slot);

    BookingRequestPage search(Optional<CommunityId> communityId, Set<BookingRequestStatus> statuses, int page,
            int size);

    boolean existsByCommunity(CommunityId communityId);

    int anonymize(UserId userId);

    int deleteWithLastDateBefore(LocalDate date);
}
