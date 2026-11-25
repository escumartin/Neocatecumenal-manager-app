package io.github.escumartin.neocatecumenal.community.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

final class CommunityApiFixture {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(500000);

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final SessionTokenIssuer tokens;

    CommunityApiFixture(JdbcTemplate jdbc, UserRepository users, SessionRepository sessions,
            SessionTokenIssuer tokens) {
        this.jdbc = jdbc;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
    }

    void reset() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room, event, "
                + "community_contact_consent, community_member, community, identity_login_pair_throttle, "
                + "identity_login_ip_throttle, identity_session, identity_user, identity_community cascade");
    }

    UUID community(int number) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into identity_community (id, number, active) values (?, ?, true)", id, number);
        jdbc.update("insert into community (id, number, status, version) values (?, ?, 'ACTIVE', 0)", id, number);
        return id;
    }

    UUID member(UUID communityId, String givenName) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into community_member (id, community_id, given_name, version) values (?, ?, ?, 0)", id,
                communityId, givenName);
        return id;
    }

    void designateResponsible(UUID communityId, UUID memberId) {
        jdbc.update("update community set responsible_member_id = ? where id = ?", memberId, communityId);
    }

    UUID user(UserRole role, UUID communityId) {
        User user = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()),
                new AccessId(ACCESS_IDS.incrementAndGet()), new DisplayName("Test Person"), role,
                new CommunityId(communityId), new PasswordHash("{argon2}hash"), false,
                new PrivacyConsent("v1", Instant.now())));
        return user.id().value();
    }

    String bearer(UserRole role, UUID communityId) {
        UUID userId = user(role, communityId);
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(new UserId(userId), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }
}
