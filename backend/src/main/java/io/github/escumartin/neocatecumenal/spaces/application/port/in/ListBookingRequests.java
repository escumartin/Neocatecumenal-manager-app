package io.github.escumartin.neocatecumenal.spaces.application.port.in;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import java.util.Set;

public interface ListBookingRequests {

    BookingRequestPage list(Actor actor, CommunityId requestedCommunity, Set<BookingRequestStatus> statuses, int page,
            int size);
}
