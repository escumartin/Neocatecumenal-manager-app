package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CommunitySpringDataRepository extends JpaRepository<CommunityJpaEntity, UUID> {

    Optional<CommunityJpaEntity> findByNumber(int number);

    List<CommunityJpaEntity> findAllByOrderByNumberAsc();
}
