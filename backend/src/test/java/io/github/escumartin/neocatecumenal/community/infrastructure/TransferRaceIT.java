package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class TransferRaceIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 20;

    @Test
    void aDeactivationCommittedBeforeTheTransferRejectsTheLateTransfer() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        linkInDatabase(member, user);

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityB, List.of(
                deactivate(administratorSession(), communityB),
                transfer(administratorSession(), member, communityB)));

        assertThat(responses.get(0).status()).isEqualTo(204);
        assertThat(responses.get(1).status()).isEqualTo(422);
        assertThat(responses.get(1).code()).isEqualTo("COMMUNITY_DEACTIVATED");
        assertThat(communityOfMember(member)).isEqualTo(communityA);
        assertThat(communityOfUser(user)).isEqualTo(communityA);
    }

    @Test
    void aTransferCommittedBeforeTheDeactivationKeepsTheMemberInTheDeactivatedCommunity() throws Exception {
        UUID member = fixture.member(communityA, "Ana");

        List<RaceHarness.Response> responses = harness.queuedBehindCommunity(communityB, List.of(
                transfer(administratorSession(), member, communityB),
                deactivate(administratorSession(), communityB)));

        assertThat(responses.get(0).status()).isEqualTo(200);
        assertThat(responses.get(1).status()).isEqualTo(204);
        assertThat(communityOfMember(member)).isEqualTo(communityB);
    }

    @Test
    void transferringAndDeactivatingTheTargetNeverEndWithAMemberMovedIntoAnAlreadyDeactivatedCommunity()
            throws Exception {
        for (int round = 0; round < ROUNDS / 2; round++) {
            UUID target = fixture.community(100 + round);
            UUID member = fixture.member(communityA, "Ana " + round);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    transfer(administratorSession(), member, target),
                    deactivate(administratorSession(), target)));

            assertThat(responses.get(1).status()).isEqualTo(204);
            assertThat(responses.get(0).status()).isIn(200, 422);
            assertThat(communityOfMember(member)).isEqualTo(responses.get(0).status() == 200 ? target : communityA);
        }
        harness.assertNoDeadlocksSinceStart();
    }

    @Test
    void crossedTransfersBetweenTwoCommunitiesBothFinishWithoutDeadlocks() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID fromA = fixture.member(communityA, "Ana " + round);
            UUID userFromA = fixture.user(UserRole.REGISTERED, communityA);
            linkInDatabase(fromA, userFromA);
            UUID fromB = fixture.member(communityB, "Berta " + round);
            UUID userFromB = fixture.user(UserRole.REGISTERED, communityB);
            linkInDatabase(fromB, userFromB);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    transfer(administratorSession(), fromA, communityB),
                    transfer(administratorSession(), fromB, communityA)));

            assertThat(responses).extracting(RaceHarness.Response::status).containsExactly(200, 200);
            assertThat(communityOfMember(fromA)).isEqualTo(communityB);
            assertThat(communityOfUser(userFromA)).isEqualTo(communityB);
            assertThat(communityOfMember(fromB)).isEqualTo(communityA);
            assertThat(communityOfUser(userFromB)).isEqualTo(communityA);
        }
        harness.assertNoDeadlocksSinceStart();
    }
}
