package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomSpringDataRepository extends JpaRepository<RoomEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.id = :id")
    Optional<RoomEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from RoomEntity r where r.id = :id")
    Optional<RoomEntity> findByIdForShare(@Param("id") UUID id);

    @Query("select r from RoomEntity r where r.active = true order by lower(r.name), r.id")
    List<RoomEntity> findAllActive();

    @Query("select count(r) > 0 from RoomEntity r where r.active = true and lower(r.name) = lower(:name)")
    boolean existsActiveByName(@Param("name") String name);
}
