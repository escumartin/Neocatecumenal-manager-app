package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class TransferVsUserChangeRaceIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 10;

    private String nameOf(UUID user) {
        return jdbc.queryForObject("select name from identity_user where id = ?", String.class, user);
    }

    private String roleOf(UUID user) {
        return jdbc.queryForObject("select role from identity_user where id = ?", String.class, user);
    }

    @Test
    void aRenameCommittedWhileTheTransferWaitsForTheUserLockSurvivesTheTransfer() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        linkInDatabase(member, user);

        List<RaceHarness.Response> responses = harness.queuedBehindUser(user,
                connection -> connection.update("update identity_user set name = 'Renamed Person', "
                        + "version = version + 1 where id = ?", user),
                List.of(transfer(administratorSession(), member, communityB)));

        assertThat(responses.get(0).status()).isEqualTo(200);
        assertThat(communityOfUser(user)).isEqualTo(communityB);
        assertThat(communityOfMember(member)).isEqualTo(communityB);
        assertThat(nameOf(user)).isEqualTo("Renamed Person");
    }

    @Test
    void aRoleChangeCommittedWhileTheTransferWaitsForTheUserLockSurvivesTheTransfer() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        fixture.user(UserRole.MODERATOR, communityA);
        linkInDatabase(member, user);

        List<RaceHarness.Response> responses = harness.queuedBehindUser(user,
                connection -> connection.update("update identity_user set role = 'MODERATOR', "
                        + "version = version + 1 where id = ?", user),
                List.of(transfer(administratorSession(), member, communityB)));

        assertThat(responses.get(0).status()).isEqualTo(200);
        assertThat(communityOfUser(user)).isEqualTo(communityB);
        assertThat(roleOf(user)).isEqualTo("MODERATOR");
    }

    @Test
    void renamingTheUserAndTransferringItsMemberNeverLoseTheRename() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID member = fixture.member(communityA, "Ana " + round);
            UUID user = fixture.user(UserRole.REGISTERED, communityA);
            linkInDatabase(member, user);
            String renamed = "Renamed " + round;

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    rename(administratorSession(), user, communityA, renamed),
                    transfer(administratorSession(), member, communityB)));

            assertThat(responses.get(1).status()).isEqualTo(200);
            assertThat(communityOfUser(user)).isEqualTo(communityB);
            assertThat(communityOfMember(member)).isEqualTo(communityB);
            if (responses.get(0).status() == 200) {
                assertThat(nameOf(user)).isEqualTo(renamed);
            }
            assertThat(linksAcrossCommunities()).isZero();
        }
        harness.assertNoDeadlocksSinceStart();
    }

    private java.util.concurrent.Callable<RaceHarness.Response> rename(String bearer, UUID user, UUID community,
            String name) {
        return () -> harness.send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/users/" + user).header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"role\":\"REGISTERED\",\"communityId\":\"" + community
                        + "\"}"));
    }
}
