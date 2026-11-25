package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.events;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.AnonymizeRequester;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component("spacesUserDeletedListener")
public class UserDeletedListener {

    private final AnonymizeRequester anonymizeRequester;

    public UserDeletedListener(AnonymizeRequester anonymizeRequester) {
        this.anonymizeRequester = anonymizeRequester;
    }

    @EventListener
    public void on(UserDeleted event) {
        anonymizeRequester.anonymize(new UserId(event.userId()));
    }
}
