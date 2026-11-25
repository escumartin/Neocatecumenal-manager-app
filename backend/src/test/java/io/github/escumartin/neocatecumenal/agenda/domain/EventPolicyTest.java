package io.github.escumartin.neocatecumenal.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventPolicyTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, OWN);
    }

    @Test
    void registeredUserIsForbiddenWithoutRequestedCommunity() {
        assertThatThrownBy(() -> EventPolicy.actingCommunity(actor(ActorRole.REGISTERED), null))
                .isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void registeredUserIsForbiddenEvenForTheirOwnCommunity() {
        assertThatThrownBy(() -> EventPolicy.actingCommunity(actor(ActorRole.REGISTERED), OWN))
                .isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void moderatorActsForTheirCommunityWhenNoneIsRequested() {
        assertThat(EventPolicy.actingCommunity(actor(ActorRole.MODERATOR), null)).isEqualTo(OWN);
    }

    @Test
    void moderatorActsForTheirCommunityWhenRequestingIt() {
        assertThat(EventPolicy.actingCommunity(actor(ActorRole.MODERATOR), OWN)).isEqualTo(OWN);
    }

    @Test
    void moderatorIsForbiddenFromActingForAnotherCommunity() {
        assertThatThrownBy(() -> EventPolicy.actingCommunity(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void administratorActsForTheRequestedCommunity() {
        assertThat(EventPolicy.actingCommunity(actor(ActorRole.ADMINISTRATOR), OTHER)).isEqualTo(OTHER);
    }

    @Test
    void administratorMustChooseACommunity() {
        assertThatThrownBy(() -> EventPolicy.actingCommunity(actor(ActorRole.ADMINISTRATOR), null))
                .isInstanceOfSatisfying(AgendaFieldInvalid.class, exception -> {
                    assertThat(exception.field()).isEqualTo("communityId");
                    assertThat(exception.code()).isEqualTo("COMMUNITY_REQUIRED");
                });
    }

    @Test
    void moderatorCanAccessTheirOwnCommunity() {
        assertThatCode(() -> EventPolicy.requireAccessTo(actor(ActorRole.MODERATOR), OWN)).doesNotThrowAnyException();
    }

    @Test
    void moderatorIsForbiddenFromAnotherCommunity() {
        assertThatThrownBy(() -> EventPolicy.requireAccessTo(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void administratorCanAccessAnyCommunity() {
        assertThatCode(() -> EventPolicy.requireAccessTo(actor(ActorRole.ADMINISTRATOR), OTHER))
                .doesNotThrowAnyException();
    }

    @Test
    void registeredUserIsForbiddenFromAnyCommunity() {
        assertThatThrownBy(() -> EventPolicy.requireAccessTo(actor(ActorRole.REGISTERED), OWN))
                .isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void registeredUserIsNotStaff() {
        assertThatThrownBy(() -> EventPolicy.requireStaff(actor(ActorRole.REGISTERED)))
                .isInstanceOf(AgendaForbidden.class);
    }

    @Test
    void moderatorAndAdministratorAreStaff() {
        assertThatCode(() -> EventPolicy.requireStaff(actor(ActorRole.MODERATOR))).doesNotThrowAnyException();
        assertThatCode(() -> EventPolicy.requireStaff(actor(ActorRole.ADMINISTRATOR))).doesNotThrowAnyException();
    }

    @Test
    void canSeeCommunityIsTrueForTheOwnCommunityOfAModeratorAndAnyForAnAdministrator() {
        assertThat(EventPolicy.canSee(actor(ActorRole.MODERATOR), OWN)).isTrue();
        assertThat(EventPolicy.canSee(actor(ActorRole.ADMINISTRATOR), OTHER)).isTrue();
    }

    @Test
    void canSeeCommunityIsFalseForAnotherCommunityOfAModeratorAndForARegisteredUser() {
        assertThat(EventPolicy.canSee(actor(ActorRole.MODERATOR), OTHER)).isFalse();
        assertThat(EventPolicy.canSee(actor(ActorRole.REGISTERED), OWN)).isFalse();
    }
}
