package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class LinkCommunityChangeRaceIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 10;

    @Test
    void linkingAndChangingTheCommunityNeverLeaveALinkAcrossCommunities() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID member = fixture.member(communityA, "Ana " + round);
            UUID user = fixture.user(UserRole.REGISTERED, communityA);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    link(administratorSession(), member, user),
                    changeUser(administratorSession(), user, UserRole.REGISTERED, communityB)));

            RaceHarness.Response linked = responses.get(0);
            RaceHarness.Response moved = responses.get(1);
            boolean linkWon = linked.status() == 200;
            assertThat(linked.status()).isIn(200, 422);
            assertThat(moved.status()).isIn(200, 409);
            assertThat(linkWon).isEqualTo(moved.status() == 409);
            assertThat(linksAcrossCommunities()).isZero();
        }
        harness.assertNoDeadlocksSinceStart();
    }

    @Test
    void aLinkCommittedBeforeTheChangeBlocksTheChange() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityA, List.of(
                link(administratorSession(), member, user),
                changeUser(administratorSession(), user, UserRole.REGISTERED, communityB)));

        assertThat(responses.get(0).status()).isEqualTo(200);
        assertThat(responses.get(1).status()).isEqualTo(409);
        assertThat(responses.get(1).code()).isEqualTo("USER_LINKED_TO_MEMBER");
        assertThat(communityOfUser(user)).isEqualTo(communityA);
        assertThat(linkedUserOf(member)).isEqualTo(user);
    }

    @Test
    void aChangeCommittedBeforeTheLinkRejectsTheLateLink() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityA, List.of(
                changeUser(administratorSession(), user, UserRole.REGISTERED, communityB),
                link(administratorSession(), member, user)));

        assertThat(responses.get(0).status()).isEqualTo(200);
        assertThat(responses.get(1).status()).isEqualTo(422);
        assertThat(responses.get(1).code()).isEqualTo("USER_NOT_IN_COMMUNITY");
        assertThat(linkedUserOf(member)).isNull();
        assertThat(communityOfUser(user)).isEqualTo(communityB);
    }

    @Test
    void aRolePromotionCommittedWhileTheLinkWaitsForTheUserIsSeenByTheLink() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        String moderator = fixture.bearer(UserRole.MODERATOR, communityA);

        List<RaceHarness.Response> responses = harness.queuedBehindUser(user,
                connection -> connection.update("update identity_user set role = 'MODERATOR' where id = ?", user),
                List.of(link(moderator, member, user)));

        assertThat(responses.get(0).status()).isEqualTo(403);
        assertThat(linkedUserOf(member)).isNull();
    }

    @Test
    void linkingAndPromotingToModeratorNeverLetsAModeratorLinkAnotherModerator() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID member = fixture.member(communityA, "Ana " + round);
            UUID user = fixture.user(UserRole.REGISTERED, communityA);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    link(fixture.bearer(UserRole.MODERATOR, communityA), member, user),
                    changeUser(administratorSession(), user, UserRole.MODERATOR, communityA)));

            assertThat(responses.get(0).status()).isIn(200, 403);
            assertThat(responses.get(1).status()).isEqualTo(200);
        }
        harness.assertNoDeadlocksSinceStart();
    }
}
