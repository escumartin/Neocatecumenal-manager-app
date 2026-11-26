package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import io.github.escumartin.neocatecumenal.identity.application.port.in.PurgeExpiredAccessData;
import io.github.escumartin.neocatecumenal.identity.application.port.in.PurgeExpiredErasures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
class AccessDataPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AccessDataPurgeJob.class);

    private final PurgeExpiredAccessData purge;
    private final PurgeExpiredErasures purgeErasures;

    AccessDataPurgeJob(PurgeExpiredAccessData purge, PurgeExpiredErasures purgeErasures) {
        this.purge = purge;
        this.purgeErasures = purgeErasures;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void purgeExpired() {
        try {
            purge.purge();
        } catch (RuntimeException e) {
            log.atError().setCause(e).log("Fallo al purgar datos de acceso caducados");
        }
        try {
            purgeErasures.purge();
        } catch (RuntimeException e) {
            log.atError().setCause(e).log("Fallo al purgar el registro de supresiones caducado");
        }
    }
}
