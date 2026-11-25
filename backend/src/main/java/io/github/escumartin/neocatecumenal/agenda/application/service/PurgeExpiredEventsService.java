package io.github.escumartin.neocatecumenal.agenda.application.service;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.PurgeExpiredEvents;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class PurgeExpiredEventsService implements PurgeExpiredEvents {

    private static final Logger log = LoggerFactory.getLogger(PurgeExpiredEventsService.class);

    private final EventRepository events;
    private final Clock clock;

    public PurgeExpiredEventsService(EventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void purge() {
        int deleted = events.deleteWithDateBefore(LocalDate.now(clock).minusYears(1));
        if (deleted > 0) {
            log.atInfo().addKeyValue("events", deleted).log("Eventos caducados eliminados");
        }
    }
}
