package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.PurgeExpiredBookings;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class PurgeExpiredBookingsService implements PurgeExpiredBookings {

    private static final Logger log = LoggerFactory.getLogger(PurgeExpiredBookingsService.class);

    private final BookingRequestRepository requests;
    private final Clock clock;

    public PurgeExpiredBookingsService(BookingRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void purge() {
        int deleted = requests.deleteWithLastDateBefore(LocalDate.now(clock).minusYears(1));
        if (deleted > 0) {
            log.atInfo().addKeyValue("requests", deleted).log("Solicitudes de salón caducadas eliminadas");
        }
    }
}
