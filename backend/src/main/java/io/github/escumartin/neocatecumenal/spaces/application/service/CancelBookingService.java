package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class CancelBookingService implements CancelBooking {

    private static final Logger log = LoggerFactory.getLogger(CancelBookingService.class);

    private final BookingRequestRepository requests;
    private final Clock clock;

    public CancelBookingService(BookingRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BookingRequest cancel(Actor actor, BookingRequestId requestId, BookingId bookingId) {
        BookingPolicy.requireStaff(actor);
        BookingRequest request = requests.findById(requestId).orElseThrow(BookingRequestNotFound::new);
        request.cancelBooking(actor, bookingId, LocalDateTime.now(clock), clock.instant());
        BookingRequest saved = requests.save(request);
        log.atInfo().addKeyValue("bookingRequestId", saved.id().value())
                .addKeyValue("bookingId", bookingId.value()).log("Ocurrencia de salón cancelada");
        return saved;
    }
}
