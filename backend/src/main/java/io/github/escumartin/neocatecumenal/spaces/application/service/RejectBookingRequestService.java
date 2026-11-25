package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.RejectBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.RejectionReason;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RejectBookingRequestService implements RejectBookingRequest {

    private static final Logger log = LoggerFactory.getLogger(RejectBookingRequestService.class);

    private final BookingRequestRepository requests;
    private final Clock clock;

    public RejectBookingRequestService(BookingRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BookingRequest reject(Actor actor, BookingRequestId id, RejectionReason reason) {
        BookingPolicy.requireAdministrator(actor);
        BookingRequest request = requests.findById(id).orElseThrow(BookingRequestNotFound::new);
        request.reject(actor, reason, clock.instant());
        BookingRequest saved = requests.save(request);
        log.atInfo().addKeyValue("bookingRequestId", saved.id().value()).addKeyValue("withReason", reason != null)
                .log("Solicitud de salón rechazada");
        return saved;
    }
}
