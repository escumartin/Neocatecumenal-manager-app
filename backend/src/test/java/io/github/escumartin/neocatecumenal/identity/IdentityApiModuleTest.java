package io.github.escumartin.neocatecumenal.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ApplicationModuleTest
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class IdentityApiModuleTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private IdentityApi identityApi;

    @MockitoBean
    private CurrentSessionContext currentSession;

    @Autowired
    private UserRepository users;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
    }

    private Community community(int number) {
        return communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(number)));
    }

    private User user(Community community, PrivacyConsent consent, boolean passwordPending) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(345678),
                new DisplayName("Test Person"), UserRole.MODERATOR, community.id(), new PasswordHash("{argon2}hash"),
                passwordPending, consent));
    }

    private void signIn(User user) {
        when(currentSession.current()).thenReturn(Optional.of(new CurrentSession(UUID.randomUUID(), user.id())));
    }

    @Test
    void currentUserIsEmptyWithoutASession() {
        when(currentSession.current()).thenReturn(Optional.empty());

        assertThat(identityApi.currentUser()).isEmpty();
    }

    @Test
    void currentUserIsEmptyWhileFirstAccessStepsArePending() {
        User pending = user(community(4), null, true);
        signIn(pending);

        assertThat(identityApi.currentUser()).isEmpty();
    }

    @Test
    void currentUserReturnsTheSignedInUser() {
        Community community = community(4);
        User ready = user(community, new PrivacyConsent("v1", Instant.now()), false);
        signIn(ready);

        assertThat(identityApi.currentUser()).contains(new CurrentUser(ready.id().value(), 345678, "Test Person",
                Role.MODERATOR, community.id().value()));
    }

    @Test
    void communitiesAreOrderedByNumber() {
        Community nine = community(9);
        Community two = community(2);

        assertThat(identityApi.communities()).containsExactly(
                new CommunityRef(two.id().value(), 2, true), new CommunityRef(nine.id().value(), 9, true));
    }

    @Test
    void communityIsFoundByIdAndEmptyWhenUnknown() {
        Community four = community(4);

        assertThat(identityApi.community(four.id().value())).contains(new CommunityRef(four.id().value(), 4, true));
        assertThat(identityApi.community(UUID.randomUUID())).isEmpty();
    }

    @Test
    void onlyTheModuleRootIsExposedToOtherModules() {
        var identity = ApplicationModules.of(io.github.escumartin.neocatecumenal.NeocatecumenalApplication.class)
                .getModuleByName("identity").orElseThrow();

        var exposed = identity.getNamedInterfaces().getUnnamedInterface().asJavaClasses()
                .map(type -> type.getName())
                .collect(Collectors.toSet());

        assertThat(exposed).containsExactlyInAnyOrder(
                IdentityApi.class.getName(), CurrentUser.class.getName(), Role.class.getName(),
                CommunityRef.class.getName(), UserDeleted.class.getName(), UserRef.class.getName(),
                IdentityRuleViolation.class.getName(), StaleLockOrderException.class.getName(),
                CommunityCatalog.class.getName(), UserLinkGuard.class.getName(),
                CommunityProvisioner.class.getName());
    }
}
