package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.identity.application.port.in.UserPage;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdAlreadyTakenException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceAdapter implements UserRepository {

    private static final String ACCESS_ID_CONSTRAINT = "uk_identity_user_access_id";

    private final UserSpringDataRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    UserPersistenceAdapter(UserSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        try {
            return toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException violation) {
            String cause = Objects.toString(NestedExceptionUtils.getMostSpecificCause(violation).getMessage(), "");
            if (cause.contains(ACCESS_ID_CONSTRAINT)) {
                throw new AccessIdAlreadyTakenException();
            }
            throw violation;
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        return repository.findById(id.value()).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByIdForUpdate(UserId id) {
        return repository.findByIdForUpdate(id.value()).map(this::refreshed);
    }

    @Override
    public Optional<User> lockForShare(UserId id) {
        return repository.findByIdForShare(id.value()).map(this::refreshed);
    }

    @Override
    public List<User> findAllByCommunity(CommunityId communityId) {
        return repository.findByCommunityIdOrderByNameAscIdAsc(communityId.value()).stream()
                .map(UserPersistenceAdapter::toDomain).toList();
    }

    private User refreshed(UserJpaEntity entity) {
        entityManager.refresh(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<User> findByAccessId(AccessId accessId) {
        return repository.findByAccessId(accessId.value()).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsAdministrator() {
        return repository.existsByRole(UserRole.ADMINISTRATOR.name());
    }

    @Override
    public int lockAdministrators() {
        return repository.lockAdministratorIds().size();
    }

    @Override
    public void lockForBootstrap() {
        repository.lockBootstrap();
    }

    @Override
    public void lockCommunity(CommunityId communityId) {
        repository.lockCommunityRow(communityId.value());
    }

    @Override
    public long countModerators(CommunityId communityId) {
        return repository.countByCommunityIdAndRole(communityId.value(), UserRole.MODERATOR.name());
    }

    @Override
    public UserPage findByCommunity(CommunityId communityId, int page, int size) {
        if ((long) page * size > Integer.MAX_VALUE) {
            return new UserPage(List.of(), page, size, repository.countByCommunityId(communityId.value()));
        }
        Page<UserJpaEntity> found = repository.findByCommunityId(communityId.value(),
                PageRequest.of(page, size, Sort.by("name", "id")));
        return new UserPage(found.getContent().stream().map(UserPersistenceAdapter::toDomain).toList(), page, size,
                found.getTotalElements());
    }

    @Override
    public void delete(UserId id) {
        repository.deleteById(id.value());
        repository.flush();
    }

    private static UserJpaEntity toEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = user.id().value();
        entity.accessId = user.accessId().value();
        entity.name = user.name().value();
        entity.role = user.role().name();
        entity.communityId = user.communityId().value();
        entity.passwordHash = user.passwordHash().value();
        entity.passwordChangeRequired = user.passwordChangeRequired();
        entity.privacyNoticeVersion = user.privacyConsent().map(PrivacyConsent::noticeVersion).orElse(null);
        entity.privacyNoticeAcceptedAt = user.privacyConsent().map(PrivacyConsent::acceptedAt).orElse(null);
        entity.version = user.isNew() ? null : user.version();
        return entity;
    }

    private static User toDomain(UserJpaEntity entity) {
        PrivacyConsent consent = entity.privacyNoticeVersion == null
                ? null
                : new PrivacyConsent(entity.privacyNoticeVersion, entity.privacyNoticeAcceptedAt);
        return User.restore(
                new UserId(entity.id),
                new AccessId(entity.accessId),
                new DisplayName(entity.name),
                UserRole.valueOf(entity.role),
                new CommunityId(entity.communityId),
                new PasswordHash(entity.passwordHash),
                entity.passwordChangeRequired,
                consent,
                entity.version);
    }
}
