package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class LinkUniquenessConcurrencyIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 10;

    @Test
    void twoModeratorsLinkingTheSameMemberLeaveExactlyOneLink() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID member = fixture.member(communityA, "Ana " + round);
            UUID first = fixture.user(UserRole.REGISTERED, communityA);
            UUID second = fixture.user(UserRole.REGISTERED, communityA);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    link(fixture.bearer(UserRole.MODERATOR, communityA), member, first),
                    link(fixture.bearer(UserRole.MODERATOR, communityA), member, second)));

            assertThat(responses).extracting(RaceHarness.Response::status).containsExactlyInAnyOrder(200, 409);
            assertThat(responses).filteredOn(response -> response.status() == 409)
                    .extracting(RaceHarness.Response::code).containsExactly("MEMBER_ALREADY_LINKED");
            int winner = responses.get(0).status() == 200 ? 0 : 1;
            assertThat(linkedUserOf(member)).isEqualTo(winner == 0 ? first : second);
        }
        harness.assertNoDeadlocksSinceStart();
    }

    @Test
    void twoModeratorsLinkingTheSameUserToDifferentMembersLeaveExactlyOneLink() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID firstMember = fixture.member(communityA, "Ana " + round);
            UUID secondMember = fixture.member(communityA, "Berta " + round);
            UUID user = fixture.user(UserRole.REGISTERED, communityA);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    link(fixture.bearer(UserRole.MODERATOR, communityA), firstMember, user),
                    link(fixture.bearer(UserRole.MODERATOR, communityA), secondMember, user)));

            assertThat(responses).extracting(RaceHarness.Response::status).containsExactlyInAnyOrder(200, 409);
            assertThat(responses).filteredOn(response -> response.status() == 409)
                    .extracting(RaceHarness.Response::code).containsExactly("USER_ALREADY_LINKED");
            assertThat(jdbc.queryForObject("select count(*) from community_member where user_id = ?",
                    Integer.class, user)).isEqualTo(1);
        }
        harness.assertNoDeadlocksSinceStart();
    }
}
