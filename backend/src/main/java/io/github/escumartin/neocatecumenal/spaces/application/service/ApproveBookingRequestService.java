package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.ApproveBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ApproveBookingRequestService implements ApproveBookingRequest {

    private static final Logger log = LoggerFactory.getLogger(ApproveBookingRequestService.class);

    private final BookingRequestRepository requests;
    private final Clock clock;

    public ApproveBookingRequestService(BookingRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BookingRequest approve(Actor actor, BookingRequestId id) {
        BookingPolicy.requireAdministrator(actor);
        BookingRequest request = requests.findById(id).orElseThrow(BookingRequestNotFound::new);
        request.approve(actor, LocalDateTime.now(clock), clock.instant());
        BookingRequest saved = requests.save(request);
        log.atInfo().addKeyValue("bookingRequestId", saved.id().value()).log("Solicitud de salón aprobada");
        return saved;
    }
}
