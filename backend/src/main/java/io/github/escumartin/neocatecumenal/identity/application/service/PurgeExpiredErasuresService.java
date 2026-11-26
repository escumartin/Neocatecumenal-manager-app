package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.PurgeExpiredErasures;
import io.github.escumartin.neocatecumenal.identity.application.port.out.ErasureRecords;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class PurgeExpiredErasuresService implements PurgeExpiredErasures {

    static final Duration RETENTION = Duration.ofDays(35);

    private static final Logger log = LoggerFactory.getLogger(PurgeExpiredErasuresService.class);

    private final ErasureRecords erasures;
    private final Clock clock;

    public PurgeExpiredErasuresService(ErasureRecords erasures, Clock clock) {
        this.erasures = erasures;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void purge() {
        int purged = erasures.purgeBefore(clock.instant().minus(RETENTION));
        if (purged > 0) {
            log.atInfo().addKeyValue("rows", purged).log("Registro de supresiones caducado eliminado");
        }
    }
}
