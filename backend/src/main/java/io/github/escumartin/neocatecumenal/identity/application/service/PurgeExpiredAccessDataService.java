package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.PurgeExpiredAccessData;
import io.github.escumartin.neocatecumenal.identity.application.port.out.LoginThrottleStore;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class PurgeExpiredAccessDataService implements PurgeExpiredAccessData {

    private static final Logger log = LoggerFactory.getLogger(PurgeExpiredAccessDataService.class);

    private final SessionRepository sessions;
    private final LoginThrottleStore throttle;
    private final Clock clock;

    public PurgeExpiredAccessDataService(SessionRepository sessions, LoginThrottleStore throttle, Clock clock) {
        this.sessions = sessions;
        this.throttle = throttle;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void purge() {
        Instant now = clock.instant();
        int expiredSessions = sessions.purgeExpired(now);
        int expiredThrottles = throttle.purgeExpired(now);
        if (expiredSessions + expiredThrottles > 0) {
            log.atInfo()
                    .addKeyValue("sessions", expiredSessions)
                    .addKeyValue("throttleRows", expiredThrottles)
                    .log("Datos de acceso caducados eliminados");
        }
    }
}
