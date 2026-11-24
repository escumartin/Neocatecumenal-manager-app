package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import java.util.Optional;

public interface AuthenticateSession {

    Optional<CurrentSession> authenticate(String token);
}
