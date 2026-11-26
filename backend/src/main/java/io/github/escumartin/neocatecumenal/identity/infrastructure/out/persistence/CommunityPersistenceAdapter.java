package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class CommunityPersistenceAdapter implements CommunityRepository {

    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final CommunitySpringDataRepository repository;

    CommunityPersistenceAdapter(CommunitySpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Community save(Community community) {
        CommunityJpaEntity entity = new CommunityJpaEntity();
        entity.id = community.id().value();
        entity.number = community.number().value();
        entity.active = community.active();
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<Community> findById(CommunityId id) {
        return repository.findById(id.value()).map(CommunityPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Community> lockForShare(CommunityId id) {
        return repository.findByIdForShare(id.value()).map(CommunityPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Community> lockForUpdate(CommunityId id) {
        return repository.findByIdForUpdate(id.value()).map(CommunityPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Community> findByNumber(CommunityNumber number) {
        return repository.findByNumber(number.value()).map(CommunityPersistenceAdapter::toDomain);
    }

    @Override
    public List<Community> findAll() {
        return repository.findAllByOrderByNumberAsc().stream().map(CommunityPersistenceAdapter::toDomain).toList();
    }

    @Override
    public void setActive(CommunityId id, boolean active) {
        CommunityJpaEntity entity = repository.findById(id.value()).orElseThrow();
        entity.active = active;
        repository.saveAndFlush(entity);
    }

    @Override
    public void renumber(CommunityId id, CommunityNumber number) {
        CommunityJpaEntity entity = repository.findById(id.value()).orElseThrow();
        entity.number = number.value();
        repository.saveAndFlush(entity);
    }

    @Override
    public void delete(CommunityId id) {
        try {
            repository.deleteById(id.value());
            repository.flush();
        } catch (DataIntegrityViolationException violation) {
            if (isForeignKeyViolation(violation)) {
                throw new IdentityRuleViolation("COMMUNITY_HAS_USERS");
            }
            throw violation;
        }
    }

    private static boolean isForeignKeyViolation(DataIntegrityViolationException violation) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(violation);
        return cause instanceof SQLException sql && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState());
    }

    private static Community toDomain(CommunityJpaEntity entity) {
        return new Community(new CommunityId(entity.id), new CommunityNumber(entity.number), entity.active);
    }
}
