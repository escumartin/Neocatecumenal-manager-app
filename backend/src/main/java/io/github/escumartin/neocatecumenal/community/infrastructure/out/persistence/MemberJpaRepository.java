package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, UUID> {

    @EntityGraph(attributePaths = "consents")
    List<MemberJpaEntity> findAllByCommunityId(UUID communityId);

    @EntityGraph(attributePaths = "consents")
    Optional<MemberJpaEntity> findByUserId(UUID userId);

    long countByCommunityId(UUID communityId);

    @Query(value = "select id from community_member where id = :id for update", nativeQuery = true)
    Optional<UUID> lockRow(@Param("id") UUID id);
}
