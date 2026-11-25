package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommunityJpaRepository extends JpaRepository<CommunityJpaEntity, UUID> {

    Optional<CommunityJpaEntity> findByNumber(int number);

    List<CommunityJpaEntity> findAllByOrderByNumberAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommunityRecord c where c.id in :ids order by c.id")
    List<CommunityJpaEntity> lockAllByIdIn(@Param("ids") Collection<UUID> ids);
}
