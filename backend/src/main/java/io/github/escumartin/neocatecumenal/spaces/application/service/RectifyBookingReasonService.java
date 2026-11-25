package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.RectifyBookingReason;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingPolicy;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RectifyBookingReasonService implements RectifyBookingReason {

    private static final Logger log = LoggerFactory.getLogger(RectifyBookingReasonService.class);

    private final BookingRequestRepository requests;

    public RectifyBookingReasonService(BookingRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional
    public BookingRequest rectify(Actor actor, BookingRequestId id, Reason reason) {
        BookingPolicy.requireStaff(actor);
        BookingRequest request = requests.findById(id).orElseThrow(BookingRequestNotFound::new);
        request.rectifyReason(actor, reason);
        BookingRequest saved = requests.save(request);
        log.atInfo().addKeyValue("bookingRequestId", saved.id().value()).log("Motivo de solicitud rectificado");
        return saved;
    }
}
