package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.service.DeleteUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.UpdateUserService;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserLinkGuardIntegrationTest {

    private final UserManagementFixture fixture = new UserManagementFixture();

    private UpdateUserService updateService() {
        return new UpdateUserService(fixture.currentSession, fixture.users, fixture.communities,
                fixture.privacyNotice, fixture.guard);
    }

    private DeleteUserService deleteService() {
        return new DeleteUserService(fixture.currentSession, fixture.users, fixture.sessions, fixture.events,
                fixture.privacyNotice, fixture.guard);
    }

    private static UpdateUserCommand move(User target, Community destination) {
        return new UpdateUserCommand(target.id(), target.name(), target.role(), destination.id());
    }

    private void replaceStored(User user, UserRole role, Community community) {
        fixture.users.users.put(user.id(), User.restore(user.id(), user.accessId(), user.name(), role,
                community.id(), user.passwordHash(), user.passwordChangeRequired(),
                user.privacyConsent().orElseThrow(), user.version() + 1));
    }

    private List<String> callsBefore(String marker) {
        List<String> calls = fixture.users.calls;
        return calls.subList(0, calls.indexOf(marker));
    }

    @Test
    void updateConsultsTheGuardBeforeAnyIdentityLockWhenTheCommunityChanges() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        updateService().update(move(target, fixture.communityB));

        assertThat(fixture.users.calls).first().isEqualTo("guard.allowsCommunityChange");
        assertThat(fixture.users.findById(target.id()).orElseThrow().communityId())
                .isEqualTo(fixture.communityB.id());
    }

    @Test
    void updateDoesNotConsultTheGuardWhenTheCommunityStaysTheSame() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        updateService().update(new UpdateUserCommand(target.id(), new DisplayName("Other Name"), UserRole.REGISTERED,
                fixture.communityA.id()));

        assertThat(fixture.users.calls).doesNotContain("guard.allowsCommunityChange");
    }

    @Test
    void updateIsConflictingWhenTheGuardDoesNotAllowTheChange() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);
        fixture.guard.allowsChange = false;

        assertThatThrownBy(() -> updateService().update(move(target, fixture.communityB)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo("USER_LINKED_TO_MEMBER"));
        assertThat(fixture.users.findById(target.id()).orElseThrow().communityId())
                .isEqualTo(fixture.communityA.id());
        assertThat(fixture.users.calls).doesNotContain("lockAdministrators", "lockCommunity");
    }

    @Test
    void updateRereadsTheUserForUpdateAfterTheGuard() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        updateService().update(move(target, fixture.communityB));

        assertThat(callsBefore("findByIdForUpdate")).containsExactly("guard.allowsCommunityChange");
    }

    @Test
    void updateIsStaleWhenTheUserMovedWhileTheGuardWasLocking() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);
        Community third = fixture.communities.save(new Community(
                new CommunityId(UUID.randomUUID()), new CommunityNumber(3)));
        fixture.guard.afterGuard = () -> replaceStored(target, UserRole.REGISTERED, third);

        assertThatThrownBy(() -> updateService().update(move(target, fixture.communityB)))
                .isInstanceOf(StaleLockOrderException.class);
        assertThat(fixture.users.findById(target.id()).orElseThrow().communityId()).isEqualTo(third.id());
    }

    @Test
    void updateUsesTheRereadUserForTheModeratorCountOfItsCommunity() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);
        fixture.guard.afterGuard = () -> replaceStored(target, UserRole.MODERATOR, fixture.communityA);

        assertThatThrownBy(() -> updateService().update(new UpdateUserCommand(target.id(), target.name(),
                UserRole.REGISTERED, fixture.communityB.id())))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo("LAST_MODERATOR"));
    }

    @Test
    void deleteReleasesTheLinkBeforeAnyIdentityLock() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        deleteService().delete(target.id());

        assertThat(fixture.users.calls).first().isEqualTo("guard.releaseBeforeDeletion");
        assertThat(callsBefore("findByIdForUpdate")).containsExactly("guard.releaseBeforeDeletion");
        assertThat(fixture.guard.released).containsExactly(target.id().value());
        assertThat(fixture.users.findById(target.id())).isEmpty();
    }

    @Test
    void deleteIsStaleWhenTheUserMovedWhileTheGuardWasLocking() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);
        fixture.openSessionOf(target);
        fixture.guard.afterGuard = () -> replaceStored(target, UserRole.REGISTERED, fixture.communityB);

        assertThatThrownBy(() -> deleteService().delete(target.id())).isInstanceOf(StaleLockOrderException.class);
        assertThat(fixture.users.findById(target.id())).isPresent();
        assertThat(fixture.sessions.sessions).hasSize(1);
        assertThat(fixture.events.published).isEmpty();
    }

    @Test
    void deleteUsesTheRereadUserForTheModeratorCheck() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);
        fixture.guard.afterGuard = () -> replaceStored(target, UserRole.MODERATOR, fixture.communityA);

        assertThatThrownBy(() -> deleteService().delete(target.id()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo("LAST_MODERATOR"));
        assertThat(fixture.users.findById(target.id())).isPresent();
    }
}
