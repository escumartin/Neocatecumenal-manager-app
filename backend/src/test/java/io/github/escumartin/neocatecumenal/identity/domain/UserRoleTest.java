package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserRoleTest {

    @Test
    void administratorIncludesEveryRole() {
        assertThat(UserRole.ADMINISTRATOR.includes(UserRole.ADMINISTRATOR)).isTrue();
        assertThat(UserRole.ADMINISTRATOR.includes(UserRole.MODERATOR)).isTrue();
        assertThat(UserRole.ADMINISTRATOR.includes(UserRole.REGISTERED)).isTrue();
    }

    @Test
    void moderatorIncludesRegisteredButNotAdministrator() {
        assertThat(UserRole.MODERATOR.includes(UserRole.MODERATOR)).isTrue();
        assertThat(UserRole.MODERATOR.includes(UserRole.REGISTERED)).isTrue();
        assertThat(UserRole.MODERATOR.includes(UserRole.ADMINISTRATOR)).isFalse();
    }

    @Test
    void registeredIncludesOnlyItself() {
        assertThat(UserRole.REGISTERED.includes(UserRole.REGISTERED)).isTrue();
        assertThat(UserRole.REGISTERED.includes(UserRole.MODERATOR)).isFalse();
        assertThat(UserRole.REGISTERED.includes(UserRole.ADMINISTRATOR)).isFalse();
    }
}
