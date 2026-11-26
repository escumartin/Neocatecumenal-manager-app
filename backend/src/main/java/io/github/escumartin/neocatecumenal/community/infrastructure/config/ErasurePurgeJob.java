package io.github.escumartin.neocatecumenal.community.infrastructure.config;

import io.github.escumartin.neocatecumenal.community.application.port.in.PurgeExpiredErasures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
class ErasurePurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ErasurePurgeJob.class);

    private final PurgeExpiredErasures purge;

    ErasurePurgeJob(PurgeExpiredErasures purge) {
        this.purge = purge;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    void purgeExpired() {
        try {
            purge.purge();
        } catch (RuntimeException e) {
            log.atError().setCause(e).log("Fallo al purgar el registro de supresiones caducado");
        }
    }
}
