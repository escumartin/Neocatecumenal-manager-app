package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CommunityPersistenceAdapter implements CommunityRepository {

    private final CommunitySpringDataRepository repository;

    CommunityPersistenceAdapter(CommunitySpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Community save(Community community) {
        CommunityJpaEntity entity = new CommunityJpaEntity();
        entity.id = community.id().value();
        entity.number = community.number().value();
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<Community> findById(CommunityId id) {
        return repository.findById(id.value()).map(CommunityPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Community> findByNumber(CommunityNumber number) {
        return repository.findByNumber(number.value()).map(CommunityPersistenceAdapter::toDomain);
    }

    @Override
    public List<Community> findAll() {
        return repository.findAllByOrderByNumberAsc().stream().map(CommunityPersistenceAdapter::toDomain).toList();
    }

    private static Community toDomain(CommunityJpaEntity entity) {
        return new Community(new CommunityId(entity.id), new CommunityNumber(entity.number));
    }
}
