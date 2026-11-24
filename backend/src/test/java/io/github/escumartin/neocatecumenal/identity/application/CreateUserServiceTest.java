package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.application.port.in.CreateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreatedUserResult;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.service.CreateUserService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class CreateUserServiceTest {

    private final UserManagementFixture fixture = new UserManagementFixture();
    private final Fakes.FakeHasher hasher = new Fakes.FakeHasher();

    private CreateUserService service(Integer... accessIds) {
        return new CreateUserService(fixture.currentSession, fixture.users, fixture.communities, hasher,
                new Fakes.SequenceAccessIds(accessIds), new Fakes.FixedInitialPassword(), fixture.privacyNotice,
                TransactionOperations.withoutTransaction());
    }

    private static CreateUserCommand command(String name, UserRole role, CommunityId community) {
        return new CreateUserCommand(new DisplayName(name), role, community);
    }

    @Test
    void moderatorCreatesARegisteredUserInTheirCommunityWithAGeneratedAccessIdAndInitialPassword() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));

        CreatedUserResult result = service(654321)
                .create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityA.id()));

        assertThat(result.user().accessId()).isEqualTo(new AccessId(654321));
        assertThat(result.user().name()).isEqualTo(new DisplayName("Ana Lopez"));
        assertThat(result.user().role()).isEqualTo(UserRole.REGISTERED);
        assertThat(result.user().communityId()).isEqualTo(fixture.communityA.id());
        assertThat(result.user().passwordChangeRequired()).isTrue();
        assertThat(result.initialPassword().value()).isEqualTo("initialpass");
        assertThat(result.user().passwordHash().value()).isEqualTo("{fake}initialpass");
        assertThat(fixture.users.findById(result.user().id())).isPresent();
    }

    @Test
    void moderatorCreatingAModeratorIsDeniedAndNothingIsCreated() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        int before = fixture.users.users.size();

        assertThatThrownBy(() -> service(654321)
                .create(command("Ana Lopez", UserRole.MODERATOR, fixture.communityA.id())))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
        assertThat(fixture.users.users).hasSize(before);
    }

    @Test
    void moderatorCreatingInAnotherCommunityIsDenied() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));

        assertThatThrownBy(() -> service(654321)
                .create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityB.id())))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void registeredUserCannotCreateUsers() {
        fixture.actAs(fixture.add(UserRole.REGISTERED, fixture.communityA));

        assertThatThrownBy(() -> service(654321)
                .create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityA.id())))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void administratorCreatesAnyRoleInAnyCommunity() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        User moderator = service(111111)
                .create(command("Ana Lopez", UserRole.MODERATOR, fixture.communityB.id())).user();
        User administrator = service(222222)
                .create(command("Luis Perez", UserRole.ADMINISTRATOR, fixture.communityB.id())).user();

        assertThat(moderator.role()).isEqualTo(UserRole.MODERATOR);
        assertThat(moderator.communityId()).isEqualTo(fixture.communityB.id());
        assertThat(administrator.role()).isEqualTo(UserRole.ADMINISTRATOR);
    }

    @Test
    void unknownCommunityIsRejectedAndNothingIsCreated() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        int before = fixture.users.users.size();

        assertThatThrownBy(() -> service(654321)
                .create(command("Ana Lopez", UserRole.REGISTERED, new CommunityId(UUID.randomUUID()))))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> {
                    assertThat(e.field()).isEqualTo("communityId");
                    assertThat(e.code()).isEqualTo("COMMUNITY_NOT_FOUND");
                });
        assertThat(fixture.users.users).hasSize(before);
    }

    @Test
    void repeatedGeneratedAccessIdCreatesBothUsersWithDistinctIdentifiers() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        CreateUserService service = service(500000, 500000, 600000);

        User first = service.create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityA.id())).user();
        User second = service.create(command("Luis Perez", UserRole.REGISTERED, fixture.communityA.id())).user();

        assertThat(first.accessId()).isEqualTo(new AccessId(500000));
        assertThat(second.accessId()).isEqualTo(new AccessId(600000));
    }

    @Test
    void anAccessIdCollisionIsLoggedAsAWarningWithTheAttemptNumber() {
        User actor = fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(CreateUserService.class);
        appender.start();
        logger.addAppender(appender);
        try {
            service(actor.accessId().value(), 654321)
                    .create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityA.id()));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value)
                    .containsExactly("attempt=1");
        });
    }

    @Test
    void givesUpAfterFiveCollisionsWithoutCreatingAnything() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        fixture.add(UserRole.REGISTERED, fixture.communityA);
        int taken = fixture.users.users.values().iterator().next().accessId().value();
        int before = fixture.users.users.size();

        assertThatThrownBy(() -> service(taken, taken, taken, taken, taken, 777777)
                .create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityA.id())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.users.users).hasSize(before);
    }

    @Test
    void callerWithPendingFirstAccessStepsIsDenied() {
        User pending = fixture.users.save(User.register(new UserId(UUID.randomUUID()), new AccessId(300000), new DisplayName("Pending"), UserRole.ADMINISTRATOR,
                fixture.communityA.id(), new io.github.escumartin.neocatecumenal.identity.domain.PasswordHash("{fake}x")));
        fixture.actAs(pending);

        assertThatThrownBy(() -> service(654321)
                .create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityA.id())))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void callerWithoutSessionIsUnauthenticated() {
        assertThatThrownBy(() -> service(654321)
                .create(command("Ana Lopez", UserRole.REGISTERED, fixture.communityA.id())))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo("UNAUTHENTICATED"));
    }
}
