package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MemberAccessPolicyLinkTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, OWN);
    }

    private static LinkedUser user(ActorRole role, CommunityId community) {
        return new LinkedUser(new UserId(UUID.randomUUID()), role, community, "Test Person");
    }

    @Test
    void moderatorLinksARegisteredUserOfTheirCommunity() {
        assertThatCode(() -> MemberAccessPolicy.requireCanLink(actor(ActorRole.MODERATOR), OWN,
                user(ActorRole.REGISTERED, OWN))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MODERATOR", "ADMINISTRATOR"})
    void moderatorNeverLinksStaffUsers(ActorRole role) {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanLink(actor(ActorRole.MODERATOR), OWN,
                user(role, OWN))).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void moderatorNeverLinksAUserOfAnotherCommunity() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanLink(actor(ActorRole.MODERATOR), OWN,
                user(ActorRole.REGISTERED, OTHER))).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void moderatorNeverLinksAMemberOfAnotherCommunity() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanLink(actor(ActorRole.MODERATOR), OTHER,
                user(ActorRole.REGISTERED, OWN))).isInstanceOf(CommunityForbidden.class);
    }

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    void administratorLinksAnyRoleAndTheDomainChecksTheCommunity(ActorRole role) {
        assertThatCode(() -> MemberAccessPolicy.requireCanLink(actor(ActorRole.ADMINISTRATOR), OWN,
                user(role, OTHER))).doesNotThrowAnyException();
    }

    @Test
    void registeredUsersNeverLink() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanLink(actor(ActorRole.REGISTERED), OWN,
                user(ActorRole.REGISTERED, OWN))).isInstanceOf(CommunityForbidden.class);
    }

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    void administratorUnlinksAnyone(ActorRole role) {
        assertThatCode(() -> MemberAccessPolicy.requireCanUnlink(actor(ActorRole.ADMINISTRATOR), OTHER,
                Optional.of(user(role, OTHER)))).doesNotThrowAnyException();
    }

    @Test
    void administratorUnlinksEvenWhenTheUserNoLongerExists() {
        assertThatCode(() -> MemberAccessPolicy.requireCanUnlink(actor(ActorRole.ADMINISTRATOR), OWN,
                Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void moderatorUnlinksARegisteredUserOfTheirCommunity() {
        assertThatCode(() -> MemberAccessPolicy.requireCanUnlink(actor(ActorRole.MODERATOR), OWN,
                Optional.of(user(ActorRole.REGISTERED, OWN)))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MODERATOR", "ADMINISTRATOR"})
    void moderatorNeverUnlinksStaffUsers(ActorRole role) {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanUnlink(actor(ActorRole.MODERATOR), OWN,
                Optional.of(user(role, OWN)))).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void moderatorNeverUnlinksTheirOwnLinkedMember() {
        Actor moderator = actor(ActorRole.MODERATOR);
        LinkedUser self = new LinkedUser(moderator.id(), ActorRole.REGISTERED, OWN, "Test Person");

        assertThatThrownBy(() -> MemberAccessPolicy.requireCanUnlink(moderator, OWN, Optional.of(self)))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void moderatorNeverUnlinksWhenTheUserIsUnknown() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanUnlink(actor(ActorRole.MODERATOR), OWN,
                Optional.empty())).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void moderatorNeverUnlinksAMemberOfAnotherCommunity() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanUnlink(actor(ActorRole.MODERATOR), OTHER,
                Optional.of(user(ActorRole.REGISTERED, OTHER)))).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void theLinkedRegisteredUserNeverUnlinksThemselves() {
        Actor registered = actor(ActorRole.REGISTERED);
        LinkedUser self = new LinkedUser(registered.id(), ActorRole.REGISTERED, OWN, "Test Person");

        assertThatThrownBy(() -> MemberAccessPolicy.requireCanUnlink(registered, OWN, Optional.of(self)))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void onlyAdministratorsTransfer() {
        assertThatCode(() -> MemberAccessPolicy.requireCanTransfer(actor(ActorRole.ADMINISTRATOR)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanTransfer(actor(ActorRole.MODERATOR)))
                .isInstanceOf(CommunityForbidden.class);
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanTransfer(actor(ActorRole.REGISTERED)))
                .isInstanceOf(CommunityForbidden.class);
    }
}
