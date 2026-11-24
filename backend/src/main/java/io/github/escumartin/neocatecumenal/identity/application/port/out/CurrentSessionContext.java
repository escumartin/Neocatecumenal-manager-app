package io.github.escumartin.neocatecumenal.identity.application.port.out;

import java.util.Optional;

public interface CurrentSessionContext {

    Optional<CurrentSession> current();
}
