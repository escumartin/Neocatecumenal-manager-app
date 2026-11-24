package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.AccessId;

public interface AccessIdGenerator {

    AccessId next();
}
