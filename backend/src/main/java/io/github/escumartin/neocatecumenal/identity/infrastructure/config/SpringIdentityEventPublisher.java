package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IdentityEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringIdentityEventPublisher implements IdentityEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringIdentityEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(UserDeleted event) {
        publisher.publishEvent(event);
    }
}
