package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.Role;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest.ActorResolver;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest.UnauthenticatedException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(ActorResolver.class)
class ActorResolverTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID COMMUNITY = UUID.randomUUID();

    @MockitoBean
    private IdentityApi identity;

    @Autowired
    private ActorResolver resolver;

    private void signedInAs(Role role) {
        when(identity.currentUser()).thenReturn(Optional.of(new CurrentUser(USER, 123456, "Test Person", role,
                COMMUNITY)));
    }

    @Test
    void copiesOnlyUserRoleAndCommunity() {
        signedInAs(Role.MODERATOR);

        Actor actor = resolver.currentActor();

        assertThat(actor).isEqualTo(new Actor(new UserId(USER), ActorRole.MODERATOR, new CommunityId(COMMUNITY)));
        assertThat(Arrays.stream(Actor.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("userId", "role", "ownCommunityId");
    }

    @Test
    void mapsEveryIdentityRole() {
        signedInAs(Role.REGISTERED);
        assertThat(resolver.currentActor().role()).isEqualTo(ActorRole.REGISTERED);

        signedInAs(Role.ADMINISTRATOR);
        assertThat(resolver.currentActor().role()).isEqualTo(ActorRole.ADMINISTRATOR);
    }

    @Test
    void failsAsUnauthenticatedWithoutACurrentUser() {
        when(identity.currentUser()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.currentActor()).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void administratorCanChooseAnExistingCommunity() {
        signedInAs(Role.ADMINISTRATOR);
        UUID chosen = UUID.randomUUID();
        when(identity.community(chosen)).thenReturn(Optional.of(new CommunityRef(chosen, 7, true)));

        Optional<CommunityId> requested = resolver.requestedCommunity(resolver.currentActor(), chosen);

        assertThat(requested).contains(new CommunityId(chosen));
    }

    @Test
    void administratorChoosingAnUnknownCommunityFailsValidation() {
        signedInAs(Role.ADMINISTRATOR);
        UUID unknown = UUID.randomUUID();
        when(identity.community(unknown)).thenReturn(Optional.empty());
        Actor actor = resolver.currentActor();

        assertThatThrownBy(() -> resolver.requestedCommunity(actor, unknown))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, exception -> {
                    assertThat(exception.field()).isEqualTo("communityId");
                    assertThat(exception.code()).isEqualTo("COMMUNITY_NOT_FOUND");
                });
    }

    @Test
    void administratorWithoutChoiceGetsNoRequestedCommunity() {
        signedInAs(Role.ADMINISTRATOR);

        assertThat(resolver.requestedCommunity(resolver.currentActor(), null)).isEmpty();
    }

    @Test
    void moderatorRequestedCommunityIsPassedThroughWithoutLookup() {
        signedInAs(Role.MODERATOR);
        UUID other = UUID.randomUUID();

        Optional<CommunityId> requested = resolver.requestedCommunity(resolver.currentActor(), other);

        assertThat(requested).contains(new CommunityId(other));
        verify(identity, never()).community(any());
    }
}
