package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.util.UUID;

public record CurrentSession(UUID sessionId, UserId userId) {
}
