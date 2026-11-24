package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SessionPersistenceAdapter implements SessionRepository {

    private final SessionSpringDataRepository repository;

    SessionPersistenceAdapter(SessionSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Session save(Session session) {
        SessionJpaEntity entity = new SessionJpaEntity();
        entity.id = session.id();
        entity.userId = session.userId().value();
        entity.tokenHash = session.tokenHash();
        entity.createdAt = session.createdAt();
        entity.lastUsedAt = session.lastUsedAt();
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<Session> findByTokenHash(byte[] tokenHash) {
        return repository.findByTokenHash(tokenHash).map(SessionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void delete(UUID sessionId) {
        repository.deleteById(sessionId);
    }

    @Override
    @Transactional
    public int purgeExpired(Instant now) {
        return repository.deleteExpired(now.minus(Session.IDLE_TIMEOUT), now.minus(Session.ABSOLUTE_TIMEOUT));
    }

    @Override
    @Transactional
    public void retainMostRecentlyUsed(UserId userId, int maxSessions) {
        repository.lockUser(userId.value());
        repository.deleteAllByUserIdBeyondMostRecentlyUsed(userId.value(), maxSessions);
    }

    @Override
    @Transactional
    public void deleteAllOf(UserId userId) {
        repository.deleteAllByUserId(userId.value());
    }

    @Override
    @Transactional
    public void deleteAllOfExcept(UserId userId, UUID sessionId) {
        repository.deleteAllByUserIdExcept(userId.value(), sessionId);
    }

    private static Session toDomain(SessionJpaEntity entity) {
        return new Session(entity.id, new UserId(entity.userId), entity.tokenHash, entity.createdAt,
                entity.lastUsedAt);
    }
}
