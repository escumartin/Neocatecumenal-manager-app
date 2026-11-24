package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findByTokenHash(byte[] tokenHash);

    void delete(UUID sessionId);

    void deleteAllOf(UserId userId);

    void deleteAllOfExcept(UserId userId, UUID sessionId);

    void retainMostRecentlyUsed(UserId userId, int maxSessions);

    int purgeExpired(Instant now);
}
