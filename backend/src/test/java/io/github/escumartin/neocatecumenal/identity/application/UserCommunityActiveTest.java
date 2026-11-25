package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.application.port.in.CreateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.service.CreateUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.UpdateUserService;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class UserCommunityActiveTest {

    private final UserManagementFixture fixture = new UserManagementFixture();
    private final Community deactivated = fixture.communities.save(
            new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(3), false));

    private CreateUserService createService() {
        return new CreateUserService(fixture.currentSession, fixture.users, fixture.communities,
                new Fakes.FakeHasher(), new Fakes.SequenceAccessIds(654321), new Fakes.FixedInitialPassword(),
                fixture.privacyNotice, TransactionOperations.withoutTransaction());
    }

    private UpdateUserService updateService() {
        return new UpdateUserService(fixture.currentSession, fixture.users, fixture.communities,
                fixture.privacyNotice, fixture.guard);
    }

    private static void assertCommunityDeactivated(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(FieldValidationException.class, e -> {
            assertThat(e.field()).isEqualTo("communityId");
            assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED");
        });
    }

    @Test
    void creatingAUserInADeactivatedCommunityIsRejectedAndNothingIsCreated() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        int before = fixture.users.users.size();

        assertCommunityDeactivated(() -> createService().create(
                new CreateUserCommand(new DisplayName("Ana Lopez"), UserRole.REGISTERED, deactivated.id())));
        assertThat(fixture.users.users).hasSize(before);
    }

    @Test
    void creatingAUserReadsTheCommunityOnlyAfterLockingIt() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        createService().create(
                new CreateUserCommand(new DisplayName("Ana Lopez"), UserRole.REGISTERED, fixture.communityA.id()));

        assertThat(fixture.users.calls).containsSubsequence("lockCommunity", "communityFindById");
    }

    @Test
    void movingAUserToADeactivatedCommunityIsRejectedAndTheUserStaysPut() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        assertCommunityDeactivated(() -> updateService().update(
                new UpdateUserCommand(target.id(), target.name(), UserRole.REGISTERED, deactivated.id())));
        assertThat(fixture.users.findById(target.id()).orElseThrow().communityId())
                .isEqualTo(fixture.communityA.id());
    }

    @Test
    void movingAUserReadsTheDestinationOnlyAfterLockingIt() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        updateService().update(new UpdateUserCommand(target.id(), target.name(), UserRole.REGISTERED,
                fixture.communityB.id()));

        assertThat(fixture.users.calls).containsSubsequence("lockCommunity", "communityFindById");
    }

    @Test
    void renamingAUserOfADeactivatedCommunityIsStillAllowed() {
        User admin = fixture.add(UserRole.ADMINISTRATOR, fixture.communityA);
        fixture.actAs(admin);
        User target = fixture.add(UserRole.REGISTERED, deactivated);

        User updated = updateService().update(new UpdateUserCommand(target.id(), new DisplayName("New Name"),
                UserRole.REGISTERED, deactivated.id()));

        assertThat(updated.name()).isEqualTo(new DisplayName("New Name"));
    }
}
