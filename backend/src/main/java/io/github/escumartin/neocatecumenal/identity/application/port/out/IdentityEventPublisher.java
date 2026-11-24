package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;

public interface IdentityEventPublisher {

    void publish(UserDeleted event);
}
