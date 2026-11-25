package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MemberAccessPolicyDirectoryTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, OWN);
    }

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    void everyRoleViewsTheDirectoryOfTheirOwnCommunity(ActorRole role) {
        assertThatCode(() -> MemberAccessPolicy.requireCanViewDirectory(actor(role), OWN))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void registeredUsersAndModeratorsCannotViewTheDirectoryOfAnotherCommunity(ActorRole role) {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanViewDirectory(actor(role), OTHER))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void administratorViewsTheDirectoryOfAnyCommunity() {
        assertThatCode(() -> MemberAccessPolicy.requireCanViewDirectory(actor(ActorRole.ADMINISTRATOR), OTHER))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void registeredUsersAndModeratorsSeeMemberFilesOfTheirOwnCommunityOnly(ActorRole role) {
        assertThat(MemberAccessPolicy.canViewMember(actor(role), OWN)).isTrue();
        assertThat(MemberAccessPolicy.canViewMember(actor(role), OTHER)).isFalse();
    }

    @Test
    void administratorSeesMemberFilesOfAnyCommunity() {
        assertThat(MemberAccessPolicy.canViewMember(actor(ActorRole.ADMINISTRATOR), OTHER)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void aDeactivatedCommunityHidesItsDirectoryFromRegisteredUsersAndModerators(ActorRole role) {
        assertThatThrownBy(() -> MemberAccessPolicy.requireDirectoryAvailable(actor(role),
                CommunityStatus.DEACTIVATED)).isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED"));
    }

    @Test
    void administratorStillSeesADeactivatedCommunityDirectory() {
        assertThatCode(() -> MemberAccessPolicy.requireDirectoryAvailable(actor(ActorRole.ADMINISTRATOR),
                CommunityStatus.DEACTIVATED)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    void anActiveCommunityDirectoryIsAvailableToEveryRole(ActorRole role) {
        assertThatCode(() -> MemberAccessPolicy.requireDirectoryAvailable(actor(role), CommunityStatus.ACTIVE))
                .doesNotThrowAnyException();
    }
}
