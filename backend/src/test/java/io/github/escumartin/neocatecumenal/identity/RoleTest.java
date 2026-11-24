package io.github.escumartin.neocatecumenal.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void administratorIncludesEveryRole() {
        assertThat(Role.ADMINISTRATOR.includes(Role.ADMINISTRATOR)).isTrue();
        assertThat(Role.ADMINISTRATOR.includes(Role.MODERATOR)).isTrue();
        assertThat(Role.ADMINISTRATOR.includes(Role.REGISTERED)).isTrue();
    }

    @Test
    void moderatorIncludesRegisteredButNotAdministrator() {
        assertThat(Role.MODERATOR.includes(Role.REGISTERED)).isTrue();
        assertThat(Role.MODERATOR.includes(Role.ADMINISTRATOR)).isFalse();
    }

    @Test
    void registeredIncludesOnlyItself() {
        assertThat(Role.REGISTERED.includes(Role.REGISTERED)).isTrue();
        assertThat(Role.REGISTERED.includes(Role.MODERATOR)).isFalse();
        assertThat(Role.REGISTERED.includes(Role.ADMINISTRATOR)).isFalse();
    }
}
