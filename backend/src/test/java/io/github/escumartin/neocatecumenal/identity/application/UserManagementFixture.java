package io.github.escumartin.neocatecumenal.identity.application;

import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

final class UserManagementFixture {

    final Fakes.InMemoryUsers users = new Fakes.InMemoryUsers();
    final Fakes.InMemoryCommunities communities = new Fakes.InMemoryCommunities(users);
    final Fakes.RecordingUserLinkGuard guard = new Fakes.RecordingUserLinkGuard(users);
    final Fakes.InMemorySessions sessions = new Fakes.InMemorySessions();
    final Fakes.FakeSessionContext currentSession = new Fakes.FakeSessionContext();
    final Fakes.RecordingEvents events = new Fakes.RecordingEvents();
    final Fakes.FixedPrivacyNotice privacyNotice = new Fakes.FixedPrivacyNotice();
    final Community communityA = communities.save(new Community(new CommunityId(UUID.randomUUID()),
            new CommunityNumber(1)));
    final Community communityB = communities.save(new Community(new CommunityId(UUID.randomUUID()),
            new CommunityNumber(2)));
    private int nextAccessId = 100000;

    User add(UserRole role, Community community) {
        return add(role, community, "Test Person");
    }

    User add(UserRole role, Community community, String name) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(nextAccessId++),
                new DisplayName(name), role, community.id(), new PasswordHash("{fake}secret"), false,
                new PrivacyConsent("v2", Instant.parse("2026-01-01T00:00:00Z"))));
    }

    User actAs(User user) {
        currentSession.session = new CurrentSession(UUID.randomUUID(), user.id());
        return user;
    }

    void openSessionOf(User user) {
        sessions.save(Session.open(user.id(), ("hash-" + user.id().value()).getBytes(), Instant.now()));
    }
}
