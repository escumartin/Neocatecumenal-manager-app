package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommunityJpaRepository extends JpaRepository<CommunityJpaEntity, UUID> {

    Optional<CommunityJpaEntity> findByNumber(int number);

    List<CommunityJpaEntity> findAllByOrderByNumberAsc();

    @Query(value = "select id from community where id in :ids order by id for update", nativeQuery = true)
    List<UUID> lockRowsInOrder(@Param("ids") Collection<UUID> ids);
}
