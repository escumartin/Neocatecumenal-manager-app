package io.github.escumartin.neocatecumenal.agenda.application.service;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.AnonymizeCreator;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class AnonymizeCreatorService implements AnonymizeCreator {

    private static final Logger log = LoggerFactory.getLogger(AnonymizeCreatorService.class);

    private final EventRepository events;

    public AnonymizeCreatorService(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional
    public void anonymize(UserId userId) {
        int affected = events.anonymize(userId);
        if (affected > 0) {
            log.atInfo().addKeyValue("userId", userId.value()).addKeyValue("events", affected).log("Eventos anonimizados");
        }
    }
}
