package io.github.escumartin.neocatecumenal.agenda.infrastructure.in.events;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.AnonymizeCreator;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component("agendaUserDeletedListener")
public class UserDeletedListener {

    private final AnonymizeCreator anonymizeCreator;

    public UserDeletedListener(AnonymizeCreator anonymizeCreator) {
        this.anonymizeCreator = anonymizeCreator;
    }

    @EventListener
    public void on(UserDeleted event) {
        anonymizeCreator.anonymize(new UserId(event.userId()));
    }
}
