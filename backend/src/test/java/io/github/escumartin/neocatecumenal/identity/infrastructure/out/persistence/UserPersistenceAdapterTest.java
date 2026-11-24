package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdAlreadyTakenException;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UserPersistenceAdapterTest {

    private final UserSpringDataRepository repository = mock(UserSpringDataRepository.class);
    private final UserPersistenceAdapter adapter = new UserPersistenceAdapter(repository);

    private static User newUser() {
        return User.register(new UserId(UUID.randomUUID()), new AccessId(123456), new DisplayName("Test User"),
                UserRole.REGISTERED, new CommunityId(UUID.randomUUID()), new PasswordHash("{argon2}x"));
    }

    private static UserJpaEntity stored(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = user.id().value();
        entity.accessId = user.accessId().value();
        entity.name = user.name().value();
        entity.role = user.role().name();
        entity.communityId = user.communityId().value();
        entity.passwordHash = user.passwordHash().value();
        entity.version = 0L;
        return entity;
    }

    @Test
    void savingDoesNotQueryForExistence() {
        User user = newUser();
        when(repository.saveAndFlush(any())).thenReturn(stored(user));

        adapter.save(user);

        verify(repository, never()).existsById(any());
    }

    @Test
    void savingANewUserSendsAnEntityWithoutVersion() {
        User user = newUser();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserJpaEntity entity = invocation.getArgument(0);
            assertThat(entity.version).isNull();
            return stored(user);
        });

        assertThat(adapter.save(user).isNew()).isFalse();
    }

    @Test
    void integrityViolationWithoutMessageIsRethrown() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException(null, new RuntimeException());
        when(repository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> adapter.save(newUser())).isSameAs(violation);
    }

    @Test
    void accessIdConstraintViolationIsTranslated() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("conflict",
                new RuntimeException("duplicate key value violates unique constraint \"uk_identity_user_access_id\"")));

        assertThatThrownBy(() -> adapter.save(newUser())).isInstanceOf(AccessIdAlreadyTakenException.class);
    }
}
