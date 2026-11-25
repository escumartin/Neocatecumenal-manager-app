package io.github.escumartin.neocatecumenal.spaces.infrastructure.config;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.PurgeExpiredBookings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class BookingPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(BookingPurgeJob.class);

    private final PurgeExpiredBookings purge;

    BookingPurgeJob(PurgeExpiredBookings purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Madrid")
    void purgeExpired() {
        try {
            purge.purge();
        } catch (RuntimeException e) {
            log.atError().setCause(e).log("Fallo al purgar solicitudes de salón caducadas");
        }
    }
}
