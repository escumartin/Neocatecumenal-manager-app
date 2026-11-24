package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserSpringDataRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByAccessId(int accessId);

    boolean existsByRole(String role);

    long countByCommunityIdAndRole(UUID communityId, String role);

    long countByCommunityId(UUID communityId);

    Page<UserJpaEntity> findByCommunityId(UUID communityId, Pageable pageable);

    @Query(value = "select id from identity_user where role = 'ADMINISTRATOR' order by id for update",
            nativeQuery = true)
    List<UUID> lockAdministratorIds();

    @Query(value = "select id from identity_community where id = :id for update", nativeQuery = true)
    List<UUID> lockCommunityRow(@Param("id") UUID id);
}
