package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingPolicyTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, OWN);
    }

    @Test
    void registeredUserIsForbiddenWithoutRequestedCommunity() {
        assertThatThrownBy(() -> BookingPolicy.actingCommunity(actor(ActorRole.REGISTERED), null))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void registeredUserIsForbiddenEvenForTheirOwnCommunity() {
        assertThatThrownBy(() -> BookingPolicy.actingCommunity(actor(ActorRole.REGISTERED), OWN))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void moderatorActsForTheirCommunityWhenNoneIsRequested() {
        assertThat(BookingPolicy.actingCommunity(actor(ActorRole.MODERATOR), null)).isEqualTo(OWN);
    }

    @Test
    void moderatorActsForTheirCommunityWhenRequestingIt() {
        assertThat(BookingPolicy.actingCommunity(actor(ActorRole.MODERATOR), OWN)).isEqualTo(OWN);
    }

    @Test
    void moderatorIsForbiddenFromActingForAnotherCommunity() {
        assertThatThrownBy(() -> BookingPolicy.actingCommunity(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void administratorActsForTheRequestedCommunity() {
        assertThat(BookingPolicy.actingCommunity(actor(ActorRole.ADMINISTRATOR), OTHER)).isEqualTo(OTHER);
    }

    @Test
    void administratorMustChooseACommunity() {
        assertThatThrownBy(() -> BookingPolicy.actingCommunity(actor(ActorRole.ADMINISTRATOR), null))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, exception -> {
                    assertThat(exception.field()).isEqualTo("communityId");
                    assertThat(exception.code()).isEqualTo("COMMUNITY_REQUIRED");
                });
    }

    @Test
    void registeredUserCannotSeeAnyCommunity() {
        assertThatThrownBy(() -> BookingPolicy.visibleCommunity(actor(ActorRole.REGISTERED), null))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void moderatorSeesTheirCommunityWhenNoneIsRequested() {
        assertThat(BookingPolicy.visibleCommunity(actor(ActorRole.MODERATOR), null)).contains(OWN);
    }

    @Test
    void moderatorSeesTheirCommunityWhenRequestingIt() {
        assertThat(BookingPolicy.visibleCommunity(actor(ActorRole.MODERATOR), OWN)).contains(OWN);
    }

    @Test
    void moderatorIsForbiddenFromSeeingAnotherCommunity() {
        assertThatThrownBy(() -> BookingPolicy.visibleCommunity(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void administratorSeesAllCommunitiesWhenNoneIsRequested() {
        assertThat(BookingPolicy.visibleCommunity(actor(ActorRole.ADMINISTRATOR), null)).isEmpty();
    }

    @Test
    void administratorSeesTheRequestedCommunity() {
        assertThat(BookingPolicy.visibleCommunity(actor(ActorRole.ADMINISTRATOR), OTHER)).contains(OTHER);
    }

    @Test
    void moderatorMayAccessTheirCommunityOnly() {
        BookingPolicy.requireAccessTo(actor(ActorRole.MODERATOR), OWN);

        assertThatThrownBy(() -> BookingPolicy.requireAccessTo(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void administratorMayAccessAnyCommunity() {
        BookingPolicy.requireAccessTo(actor(ActorRole.ADMINISTRATOR), OTHER);
    }

    @Test
    void registeredUserMayNotAccessAnyCommunity() {
        assertThatThrownBy(() -> BookingPolicy.requireAccessTo(actor(ActorRole.REGISTERED), OWN))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void registeredUserIsNotStaff() {
        assertThatThrownBy(() -> BookingPolicy.requireStaff(actor(ActorRole.REGISTERED)))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void moderatorAndAdministratorAreStaff() {
        BookingPolicy.requireStaff(actor(ActorRole.MODERATOR));
        BookingPolicy.requireStaff(actor(ActorRole.ADMINISTRATOR));
    }

    @Test
    void canSeeCommunityIsTrueForTheOwnCommunityOfAModeratorAndAnyForAnAdministrator() {
        assertThat(BookingPolicy.canSee(actor(ActorRole.MODERATOR), OWN)).isTrue();
        assertThat(BookingPolicy.canSee(actor(ActorRole.ADMINISTRATOR), OTHER)).isTrue();
    }

    @Test
    void canSeeCommunityIsFalseForAnotherCommunityOfAModeratorAndForARegisteredUser() {
        assertThat(BookingPolicy.canSee(actor(ActorRole.MODERATOR), OTHER)).isFalse();
        assertThat(BookingPolicy.canSee(actor(ActorRole.REGISTERED), OWN)).isFalse();
    }
}
