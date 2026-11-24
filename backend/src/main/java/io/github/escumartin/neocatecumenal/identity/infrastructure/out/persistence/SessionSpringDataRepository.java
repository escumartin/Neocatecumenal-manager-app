package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.List;
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

    @Query(value = "select id from identity_user where id = :userId for update", nativeQuery = true)
    List<UUID> lockUser(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            delete from identity_session
            where user_id = :userId
              and id not in (
                  select id from identity_session
                  where user_id = :userId
                  order by last_used_at desc, created_at desc, id
                  limit :maxSessions)
            """, nativeQuery = true)
    void deleteAllByUserIdBeyondMostRecentlyUsed(@Param("userId") UUID userId, @Param("maxSessions") int maxSessions);

    @Modifying
    @Query("delete from SessionJpaEntity s where s.lastUsedAt <= :idleLimit or s.createdAt <= :absoluteLimit")
    int deleteExpired(@Param("idleLimit") Instant idleLimit, @Param("absoluteLimit") Instant absoluteLimit);
}
