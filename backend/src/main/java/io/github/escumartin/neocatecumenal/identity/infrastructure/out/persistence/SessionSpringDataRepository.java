package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SessionSpringDataRepository extends JpaRepository<SessionJpaEntity, UUID> {

    Optional<SessionJpaEntity> findByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("delete from SessionJpaEntity s where s.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from SessionJpaEntity s where s.userId = :userId and s.id <> :sessionId")
    void deleteAllByUserIdExcept(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId);

    @Modifying
    @Query("delete from SessionJpaEntity s where s.lastUsedAt <= :idleLimit or s.createdAt <= :absoluteLimit")
    int deleteExpired(@Param("idleLimit") Instant idleLimit, @Param("absoluteLimit") Instant absoluteLimit);
}
