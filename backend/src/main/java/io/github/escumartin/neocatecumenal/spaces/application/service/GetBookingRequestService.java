package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import org.springframework.transaction.annotation.Transactional;

public class GetBookingRequestService implements GetBookingRequest {

    private final BookingRequestRepository requests;

    public GetBookingRequestService(BookingRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingRequest get(Actor actor, BookingRequestId id) {
        BookingPolicy.requireStaff(actor);
        BookingRequest request = requests.findById(id).orElseThrow(BookingRequestNotFound::new);
        if (!BookingPolicy.canSee(actor, request.communityId())) {
            throw new BookingRequestNotFound();
        }
        return request;
    }
}
