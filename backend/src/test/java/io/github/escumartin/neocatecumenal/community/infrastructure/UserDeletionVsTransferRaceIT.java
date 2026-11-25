package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class UserDeletionVsTransferRaceIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 10;

    @Test
    void deletingTheUserAndTransferringItsMemberLeaveACoherentState() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID member = fixture.member(communityA, "Ana " + round);
            UUID user = fixture.user(UserRole.REGISTERED, communityA);
            linkInDatabase(member, user);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    deleteUser(administratorSession(), user), transfer(administratorSession(), member, communityB)));

            assertThat(responses).extracting(RaceHarness.Response::status).containsExactly(204, 200);
            assertThat(userExists(user)).isFalse();
            assertThat(linkedUserOf(member)).isNull();
            assertThat(communityOfMember(member)).isEqualTo(communityB);
        }
        harness.assertNoDeadlocksSinceStart();
    }

    @Test
    void aTransferConfirmedWhileTheDeletionWaitsMakesTheDeletionCheckTheLastModeratorAgainstTheNewCommunity()
            throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID moderator = fixture.user(UserRole.MODERATOR, communityA);
        fixture.user(UserRole.MODERATOR, communityA);
        linkInDatabase(member, moderator);

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityA,
                jdbc -> moveToCommunityB(jdbc, member, moderator),
                List.of(deleteUser(administratorSession(), moderator)));

        assertThat(responses.get(0).status()).isEqualTo(409);
        assertThat(responses.get(0).code()).isEqualTo("LAST_MODERATOR");
        assertThat(userExists(moderator)).isTrue();
        assertThat(linkedUserOf(member)).isEqualTo(moderator);
    }

    @Test
    void aTransferConfirmedWhileTheDeletionWaitsLetsTheDeletionProceedWhenTheNewCommunityHasAnotherModerator()
            throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID moderator = fixture.user(UserRole.MODERATOR, communityA);
        fixture.user(UserRole.MODERATOR, communityA);
        fixture.user(UserRole.MODERATOR, communityB);
        linkInDatabase(member, moderator);

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityA,
                jdbc -> moveToCommunityB(jdbc, member, moderator),
                List.of(deleteUser(administratorSession(), moderator)));

        assertThat(responses.get(0).status()).isEqualTo(204);
        assertThat(userExists(moderator)).isFalse();
        assertThat(linkedUserOf(member)).isNull();
        assertThat(communityOfMember(member)).isEqualTo(communityB);
    }

    private void moveToCommunityB(org.springframework.jdbc.core.JdbcTemplate connection, UUID member, UUID user) {
        connection.update("update community_member set community_id = ? where id = ?", communityB, member);
        connection.update("update identity_user set community_id = ? where id = ?", communityB, user);
    }
}
