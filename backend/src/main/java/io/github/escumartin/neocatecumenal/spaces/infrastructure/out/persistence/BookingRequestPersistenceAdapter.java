package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingRequestPersistenceAdapter implements BookingRequestRepository {

    private static final Logger log = LoggerFactory.getLogger(BookingRequestPersistenceAdapter.class);
    private static final Set<String> SLOT_COLLISION_STATES = Set.of("23P01", "40P01");
    private static final Sort ORDER = Sort.by("firstDate", "startTime", "createdAt", "id");

    private final BookingRequestSpringDataRepository repository;

    BookingRequestPersistenceAdapter(BookingRequestSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public BookingRequest save(BookingRequest request) {
        BookingRequestEntity entity = repository.findById(request.id().value())
                .orElseGet(() -> BookingRequestPersistenceMapper.newEntity(request));
        BookingRequestPersistenceMapper.copyChanges(request, entity);
        try {
            return BookingRequestPersistenceMapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataAccessException failure) {
            if (collidesOnSlot(failure)) {
                log.atInfo().log("Colisión de franja detectada por la base de datos");
                throw new RoomOccupied(List.of());
            }
            throw failure;
        }
    }

    private static boolean collidesOnSlot(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && SLOT_COLLISION_STATES.contains(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingRequest> findById(BookingRequestId id) {
        return repository.findById(id.value()).map(BookingRequestPersistenceMapper::toDomain);
    }

    @Override
    public List<Conflict> findConflicts(RoomId roomId, Collection<LocalDate> dates, TimeSlot slot) {
        return repository.findOverlapping(roomId.value(), dates, BookingStatus.activeStatuses(), slot.start(),
                slot.end()).stream()
                .map(booking -> new Conflict(booking.bookingDate,
                        new TimeSlot(booking.startTime, booking.endTime)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingRequestPage search(Optional<CommunityId> communityId, Set<BookingRequestStatus> statuses, int page,
            int size) {
        Set<BookingRequestStatus> wanted = statuses.isEmpty() ? EnumSet.allOf(BookingRequestStatus.class) : statuses;
        PageRequest pageRequest = PageRequest.of(page, size, ORDER);
        Page<BookingRequestEntity> found = communityId
                .map(community -> repository.findByCommunityIdAndStatusIn(community.value(), wanted, pageRequest))
                .orElseGet(() -> repository.findByStatusIn(wanted, pageRequest));
        return new BookingRequestPage(
                found.getContent().stream().map(BookingRequestPersistenceMapper::toDomain).toList(), page, size,
                found.getTotalElements());
    }

    @Override
    @Transactional
    public int anonymize(UserId userId) {
        int requesters = repository.clearRequester(userId.value());
        int changers = repository.clearChanger(userId.value());
        return requesters + changers;
    }

    @Override
    @Transactional
    public int deleteWithLastDateBefore(LocalDate date) {
        return repository.deleteWithLastDateBefore(date);
    }
}
