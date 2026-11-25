package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BookingRequestSpringDataRepository extends JpaRepository<BookingRequestEntity, UUID> {

    Page<BookingRequestEntity> findByStatusIn(Collection<BookingRequestStatus> statuses, Pageable pageable);

    Page<BookingRequestEntity> findByCommunityIdAndStatusIn(UUID communityId,
            Collection<BookingRequestStatus> statuses, Pageable pageable);

    @Query("""
            select b from BookingRequestEntity r join r.bookings b
            where b.roomId = :roomId and b.bookingDate in :dates and b.status in :statuses
              and b.startTime < :end and b.endTime > :start
            order by b.bookingDate, b.startTime
            """)
    List<BookingEntity> findOverlapping(@Param("roomId") UUID roomId, @Param("dates") Collection<LocalDate> dates,
            @Param("statuses") Collection<BookingStatus> statuses, @Param("start") LocalTime start,
            @Param("end") LocalTime end);

    @Query("""
            select new io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence.OccupancyRow(
                b.startTime, b.endTime, b.status, r.communityId)
            from BookingRequestEntity r join r.bookings b
            where b.roomId = :roomId and b.bookingDate = :date and b.status in :statuses
            order by b.startTime, b.id
            """)
    List<OccupancyRow> findOccupancy(@Param("roomId") UUID roomId, @Param("date") LocalDate date,
            @Param("statuses") Collection<BookingStatus> statuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update booking_request set requested_by = null where requested_by = :userId",
            nativeQuery = true)
    int clearRequester(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update booking_status_change set changed_by = null where changed_by = :userId",
            nativeQuery = true)
    int clearChanger(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from booking_request where last_date < :date", nativeQuery = true)
    int deleteWithLastDateBefore(@Param("date") LocalDate date);
}
