package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.community.infrastructure.in.rest.ActorResolver;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ActorResolverTest {

    private final IdentityApi identity = mock(IdentityApi.class);
    private final ActorResolver resolver = new ActorResolver(identity);

    @ParameterizedTest
    @EnumSource(Role.class)
    void copiesOnlyTheIdTheRoleAndTheCommunity(Role role) {
        UUID userId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        when(identity.currentUser()).thenReturn(Optional.of(
                new CurrentUser(userId, 654321, "Test Person", role, communityId)));

        Actor actor = resolver.currentActor();

        assertThat(actor).isEqualTo(new Actor(new UserId(userId), ActorRole.valueOf(role.name()),
                new CommunityId(communityId)));
    }

    @Test
    void withoutACurrentUserTheCallIsUnauthenticated() {
        when(identity.currentUser()).thenReturn(Optional.empty());

        assertThatThrownBy(resolver::currentActor).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo("UNAUTHENTICATED"));
    }
}
