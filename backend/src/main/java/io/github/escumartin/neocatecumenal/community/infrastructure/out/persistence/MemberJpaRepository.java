package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, UUID> {

    @EntityGraph(attributePaths = "consents")
    List<MemberJpaEntity> findAllByCommunityId(UUID communityId);

    @EntityGraph(attributePaths = "consents")
    Optional<MemberJpaEntity> findByUserId(UUID userId);

    long countByCommunityId(UUID communityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MemberRecord m where m.id = :id")
    Optional<MemberJpaEntity> lockById(@Param("id") UUID id);
}
