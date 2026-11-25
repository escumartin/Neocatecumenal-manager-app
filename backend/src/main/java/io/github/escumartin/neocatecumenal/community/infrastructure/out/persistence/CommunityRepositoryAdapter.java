package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityName;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CommunityRepositoryAdapter implements CommunityRepository {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String NUMBER_CONSTRAINT = "uk_community_number";

    private final CommunityJpaRepository repository;

    CommunityRepositoryAdapter(CommunityJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Community save(Community community) {
        CommunityJpaEntity entity = repository.findById(community.id().value())
                .orElseGet(() -> created(community));
        entity.number = community.number().value();
        entity.name = community.name().map(CommunityName::value).orElse(null);
        entity.status = community.status();
        entity.responsibleMemberId = community.responsibleMemberId().map(MemberId::value).orElse(null);
        try {
            return toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException failure) {
            if (isViolation(failure, UNIQUE_VIOLATION, NUMBER_CONSTRAINT)) {
                throw new CommunityFieldInvalid("number", "COMMUNITY_NUMBER_TAKEN");
            }
            throw failure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Community> findById(CommunityId id) {
        return repository.findById(id.value()).map(CommunityRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Community> findByNumber(CommunityNumber number) {
        return repository.findByNumber(number.value()).map(CommunityRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Community> findAll() {
        return repository.findAllByOrderByNumberAsc().stream().map(CommunityRepositoryAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public List<Community> lockInIdOrder(CommunityId... ids) {
        return repository.lockAllByIdIn(Arrays.stream(ids).map(CommunityId::value).toList()).stream()
                .map(CommunityRepositoryAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(CommunityId id) {
        try {
            repository.deleteById(id.value());
            repository.flush();
        } catch (DataIntegrityViolationException failure) {
            if (isViolation(failure, FOREIGN_KEY_VIOLATION, null)) {
                throw new CommunityDomainException("COMMUNITY_NOT_EMPTY");
            }
            throw failure;
        }
    }

    private static CommunityJpaEntity created(Community community) {
        CommunityJpaEntity entity = new CommunityJpaEntity();
        entity.id = community.id().value();
        return entity;
    }

    private static boolean isViolation(DataIntegrityViolationException failure, String sqlState,
            String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return sqlState.equals(violation.getSQLState())
                        && (constraint == null || constraint.equals(violation.getConstraintName()));
            }
        }
        return false;
    }

    private static Community toDomain(CommunityJpaEntity entity) {
        return Community.restore(new CommunityId(entity.id), new CommunityNumber(entity.number),
                CommunityName.of(entity.name), entity.status,
                Optional.ofNullable(entity.responsibleMemberId).map(MemberId::new));
    }
}
