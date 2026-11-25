package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingPolicyOccupancyTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, OWN);
    }

    @Test
    void anAdministratorWithoutACommunityMustChooseOne() {
        assertThatThrownBy(() -> BookingPolicy.actingCommunity(actor(ActorRole.ADMINISTRATOR), null))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("communityId");
                    assertThat(failure.code()).isEqualTo("COMMUNITY_REQUIRED");
                });
    }

    @Test
    void anAdministratorSeesOccupancyForTheChosenCommunity() {
        assertThat(BookingPolicy.actingCommunity(actor(ActorRole.ADMINISTRATOR), OTHER)).isEqualTo(OTHER);
    }

    @Test
    void aModeratorWithoutACommunityUsesTheirOwn() {
        assertThat(BookingPolicy.actingCommunity(actor(ActorRole.MODERATOR), null)).isEqualTo(OWN);
    }

    @Test
    void aModeratorCannotAskForAnotherCommunity() {
        assertThatThrownBy(() -> BookingPolicy.actingCommunity(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(SpacesForbidden.class);
    }

    @Test
    void aRegisteredUserCannotSeeOccupancy() {
        assertThatThrownBy(() -> BookingPolicy.actingCommunity(actor(ActorRole.REGISTERED), null))
                .isInstanceOf(SpacesForbidden.class);
    }
}
