package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.ListBookingRequests;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

public class ListBookingRequestsService implements ListBookingRequests {

    private final BookingRequestRepository requests;

    public ListBookingRequestsService(BookingRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingRequestPage list(Actor actor, CommunityId requestedCommunity, Set<BookingRequestStatus> statuses,
            int page, int size) {
        return requests.search(BookingPolicy.visibleCommunity(actor, requestedCommunity), statuses, page, size);
    }
}
