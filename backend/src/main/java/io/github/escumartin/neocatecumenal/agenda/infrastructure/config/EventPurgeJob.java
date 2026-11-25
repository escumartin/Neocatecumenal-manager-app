package io.github.escumartin.neocatecumenal.agenda.infrastructure.config;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.PurgeExpiredEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EventPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(EventPurgeJob.class);

    private final PurgeExpiredEvents purge;

    EventPurgeJob(PurgeExpiredEvents purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "Europe/Madrid")
    void purgeExpired() {
        try {
            purge.purge();
        } catch (RuntimeException e) {
            log.atError().setCause(e).log("Fallo al purgar eventos caducados");
        }
    }
}
