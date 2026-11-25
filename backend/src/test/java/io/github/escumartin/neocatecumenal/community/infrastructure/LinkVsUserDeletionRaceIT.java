package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class LinkVsUserDeletionRaceIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 10;

    @Test
    void linkingAndDeletingTheUserNeverLeaveADanglingLink() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID member = fixture.member(communityA, "Ana " + round);
            UUID user = fixture.user(UserRole.REGISTERED, communityA);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    link(administratorSession(), member, user), deleteUser(administratorSession(), user)));

            assertThat(responses.get(0).status()).isIn(200, 422);
            if (responses.get(0).status() == 422) {
                assertThat(responses.get(0).code()).isEqualTo("USER_NOT_FOUND");
            }
            assertThat(responses.get(1).status()).isEqualTo(204);
            assertThat(userExists(user)).isFalse();
            assertThat(danglingLinks()).isZero();
        }
        harness.assertNoDeadlocksSinceStart();
    }

    @Test
    void aLinkCommittedBeforeTheDeletionIsUndoneByTheDeletion() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityA, List.of(
                link(administratorSession(), member, user), deleteUser(administratorSession(), user)));

        assertThat(responses.get(0).status()).isEqualTo(200);
        assertThat(responses.get(1).status()).isEqualTo(204);
        assertThat(userExists(user)).isFalse();
        assertThat(linkedUserOf(member)).isNull();
    }

    @Test
    void aDeletionCommittedBeforeTheLinkRejectsTheLateLinkAsUserNotFound() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityA, List.of(
                deleteUser(administratorSession(), user), link(administratorSession(), member, user)));

        assertThat(responses.get(0).status()).isEqualTo(204);
        assertThat(responses.get(1).status()).isEqualTo(422);
        assertThat(responses.get(1).code()).isEqualTo("USER_NOT_FOUND");
        assertThat(linkedUserOf(member)).isNull();
        assertThat(danglingLinks()).isZero();
    }
}
