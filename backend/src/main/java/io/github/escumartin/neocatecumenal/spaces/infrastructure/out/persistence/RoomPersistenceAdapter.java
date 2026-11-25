package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomConcurrentlyModified;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNameTaken;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class RoomPersistenceAdapter implements RoomRepository {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String ACTIVE_NAME_INDEX = "uk_room_active_name";

    private final RoomSpringDataRepository repository;
    private final Clock clock;

    RoomPersistenceAdapter(RoomSpringDataRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<Room> findById(RoomId id) {
        return repository.findById(id.value()).map(RoomPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Room> findByIdForUpdate(RoomId id) {
        return repository.findByIdForUpdate(id.value()).map(RoomPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Room> findByIdForShare(RoomId id) {
        return repository.findByIdForShare(id.value()).map(RoomPersistenceAdapter::toDomain);
    }

    @Override
    public List<Room> findAllActive() {
        return repository.findAllActive().stream().map(RoomPersistenceAdapter::toDomain).toList();
    }

    @Override
    public boolean existsActiveByName(RoomName name) {
        return repository.existsActiveByName(name.value());
    }

    @Override
    public Room save(Room room) {
        RoomEntity entity = repository.findById(room.id().value()).orElseGet(() -> created(room));
        entity.name = room.name().value();
        entity.active = room.active();
        try {
            return toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException failure) {
            if (isActiveNameViolation(failure)) {
                throw new RoomNameTaken();
            }
            throw failure;
        } catch (OptimisticLockingFailureException failure) {
            throw new RoomConcurrentlyModified();
        }
    }

    private RoomEntity created(Room room) {
        RoomEntity entity = new RoomEntity();
        entity.id = room.id().value();
        entity.createdAt = clock.instant();
        return entity;
    }

    private static boolean isActiveNameViolation(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return UNIQUE_VIOLATION.equals(violation.getSQLState())
                        && ACTIVE_NAME_INDEX.equals(violation.getConstraintName());
            }
        }
        return false;
    }

    private static Room toDomain(RoomEntity entity) {
        return Room.restore(new RoomId(entity.id), new RoomName(entity.name), entity.active);
    }
}
