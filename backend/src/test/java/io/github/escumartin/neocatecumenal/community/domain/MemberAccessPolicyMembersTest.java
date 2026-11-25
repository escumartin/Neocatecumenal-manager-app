package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MemberAccessPolicyMembersTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, OWN);
    }

    @Test
    void moderatorManagesMembersOfTheirOwnCommunity() {
        assertThatCode(() -> MemberAccessPolicy.requireCanManageMembers(actor(ActorRole.MODERATOR), OWN))
                .doesNotThrowAnyException();
    }

    @Test
    void moderatorCannotManageMembersOfAnotherCommunity() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanManageMembers(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(CommunityForbidden.class);
    }

    @ParameterizedTest
    @EnumSource(value = CommunityIdChoice.class)
    void administratorManagesMembersOfAnyCommunity(CommunityIdChoice choice) {
        assertThatCode(() -> MemberAccessPolicy.requireCanManageMembers(actor(ActorRole.ADMINISTRATOR),
                choice.id)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = CommunityIdChoice.class)
    void registeredUsersManageNothing(CommunityIdChoice choice) {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanManageMembers(actor(ActorRole.REGISTERED), choice.id))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void managingIsAnswerableAsABooleanWithoutThrowing() {
        org.assertj.core.api.Assertions.assertThat(
                MemberAccessPolicy.canManageMembers(actor(ActorRole.MODERATOR), OTHER)).isFalse();
        org.assertj.core.api.Assertions.assertThat(
                MemberAccessPolicy.canManageMembers(actor(ActorRole.MODERATOR), OWN)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MODERATOR", "ADMINISTRATOR"})
    void staffMayManageMembersInGeneral(ActorRole role) {
        assertThatCode(() -> MemberAccessPolicy.requireCanManageMembers(actor(role))).doesNotThrowAnyException();
    }

    @Test
    void registeredUsersMayNotManageMembersInGeneral() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanManageMembers(actor(ActorRole.REGISTERED)))
                .isInstanceOf(CommunityForbidden.class);
    }

    enum CommunityIdChoice {
        OWN_COMMUNITY(OWN), OTHER_COMMUNITY(OTHER);

        private final CommunityId id;

        CommunityIdChoice(CommunityId id) {
            this.id = id;
        }
    }
}
