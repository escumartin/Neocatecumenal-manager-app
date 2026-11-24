package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;

public interface InitialPasswordGenerator {

    PlainPassword next();
}
