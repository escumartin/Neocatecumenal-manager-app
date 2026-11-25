package io.github.escumartin.neocatecumenal.agenda.infrastructure.out.persistence;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EventSpringDataRepository extends JpaRepository<EventEntity, UUID> {

    boolean existsByCommunityId(UUID communityId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update event set created_by = null where created_by = :userId", nativeQuery = true)
    int clearCreator(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from event where event_date < :date", nativeQuery = true)
    int deleteWithDateBefore(@Param("date") LocalDate date);
}
