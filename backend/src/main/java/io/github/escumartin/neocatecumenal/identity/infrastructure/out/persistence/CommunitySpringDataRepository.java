package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommunitySpringDataRepository extends JpaRepository<CommunityJpaEntity, UUID> {

    @Query(value = "select * from identity_community where id = :id for share", nativeQuery = true)
    Optional<CommunityJpaEntity> findByIdForShare(@Param("id") UUID id);

    @Query(value = "select * from identity_community where id = :id for update", nativeQuery = true)
    Optional<CommunityJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<CommunityJpaEntity> findByNumber(int number);

    List<CommunityJpaEntity> findAllByOrderByNumberAsc();
}
