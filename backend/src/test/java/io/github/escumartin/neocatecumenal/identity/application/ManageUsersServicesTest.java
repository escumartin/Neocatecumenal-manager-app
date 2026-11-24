package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListUsersQuery;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserPage;
import io.github.escumartin.neocatecumenal.identity.application.service.DeleteUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.GetUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.ListUsersService;
import io.github.escumartin.neocatecumenal.identity.application.service.UpdateUserService;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageUsersServicesTest {

    private final UserManagementFixture fixture = new UserManagementFixture();

    private UpdateUserService updateService() {
        return new UpdateUserService(fixture.currentSession, fixture.users, fixture.communities,
                fixture.privacyNotice);
    }

    private DeleteUserService deleteService() {
        return new DeleteUserService(fixture.currentSession, fixture.users, fixture.sessions, fixture.events,
                fixture.privacyNotice);
    }

    private ListUsersService listService() {
        return new ListUsersService(fixture.currentSession, fixture.users, fixture.communities,
                fixture.privacyNotice);
    }

    private GetUserService getService() {
        return new GetUserService(fixture.currentSession, fixture.users, fixture.privacyNotice);
    }

    private static UpdateUserCommand update(User target, String name, UserRole role, CommunityId community) {
        return new UpdateUserCommand(target.id(), new DisplayName(name), role, community);
    }

    private static void assertDomainCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void moderatorRenamesARegisteredUserOfTheirCommunity() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        User updated = updateService()
                .update(update(target, "New Name", UserRole.REGISTERED, fixture.communityA.id()));

        assertThat(updated.name()).isEqualTo(new DisplayName("New Name"));
        assertThat(fixture.users.findById(target.id()).orElseThrow().name()).isEqualTo(new DisplayName("New Name"));
    }

    @Test
    void moderatorCannotRenameARegisteredUserOfAnotherCommunity() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityB);

        assertDomainCode(() -> updateService()
                .update(update(target, "New Name", UserRole.REGISTERED, fixture.communityB.id())), "FORBIDDEN");
    }

    @Test
    void moderatorCannotChangeRoleOrCommunity() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        assertDomainCode(() -> updateService()
                .update(update(target, "Same", UserRole.MODERATOR, fixture.communityA.id())), "FORBIDDEN");
        assertDomainCode(() -> updateService()
                .update(update(target, "Same", UserRole.REGISTERED, fixture.communityB.id())), "FORBIDDEN");
        assertThat(fixture.users.findById(target.id()).orElseThrow().role()).isEqualTo(UserRole.REGISTERED);
    }

    @Test
    void administratorChangesRoleAndCommunity() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        User updated = updateService()
                .update(update(target, "Same", UserRole.MODERATOR, fixture.communityB.id()));

        assertThat(updated.role()).isEqualTo(UserRole.MODERATOR);
        assertThat(updated.communityId()).isEqualTo(fixture.communityB.id());
    }

    @Test
    void administratorCannotChangeTheirOwnRole() {
        User administrator = fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        fixture.add(UserRole.ADMINISTRATOR, fixture.communityA);

        assertDomainCode(() -> updateService()
                .update(update(administrator, "Same", UserRole.MODERATOR, fixture.communityA.id())),
                "SELF_ACTION_FORBIDDEN");
    }

    @Test
    void administratorCanRenameThemselvesKeepingRoleAndCommunity() {
        User administrator = fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        User updated = updateService()
                .update(update(administrator, "Renamed", UserRole.ADMINISTRATOR, fixture.communityA.id()));

        assertThat(updated.name()).isEqualTo(new DisplayName("Renamed"));
    }

    @Test
    void demotingAnAdministratorWhoIsTheLastOneOnceLockedIsRejected() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.ADMINISTRATOR, fixture.communityA);
        fixture.users.administratorsSeenAfterLock = 1;

        assertDomainCode(() -> updateService()
                .update(update(target, "Same", UserRole.REGISTERED, fixture.communityA.id())), "LAST_ADMINISTRATOR");
        assertThat(fixture.users.calls).first().isEqualTo("lockAdministrators");
        assertThat(fixture.users.findById(target.id()).orElseThrow().role()).isEqualTo(UserRole.ADMINISTRATOR);
    }

    @Test
    void demotingTheLastModeratorIsRejectedAfterLockingTheCommunity() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityB));
        User target = fixture.add(UserRole.MODERATOR, fixture.communityA);

        assertDomainCode(() -> updateService()
                .update(update(target, "Same", UserRole.REGISTERED, fixture.communityA.id())), "LAST_MODERATOR");
        assertThat(fixture.users.calls).containsExactly("lockCommunity", "countModerators");
    }

    @Test
    void movingToAnUnknownCommunityIsRejected() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        assertThatThrownBy(() -> updateService()
                .update(update(target, "Same", UserRole.REGISTERED, new CommunityId(UUID.randomUUID()))))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void updatingAMissingUserIsNotFound() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        assertThatThrownBy(() -> updateService().update(new UpdateUserCommand(new UserId(UUID.randomUUID()),
                new DisplayName("Same"), UserRole.REGISTERED, fixture.communityA.id())))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void registeredUserCannotUpdateAnyone() {
        User registered = fixture.actAs(fixture.add(UserRole.REGISTERED, fixture.communityA));

        assertDomainCode(() -> updateService()
                .update(update(registered, "Same", UserRole.REGISTERED, fixture.communityA.id())), "FORBIDDEN");
    }

    @Test
    void moderatorDeletesARegisteredUserOfTheirCommunityClosingSessionsAndPublishingUserDeleted() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);
        fixture.openSessionOf(target);

        deleteService().delete(target.id());

        assertThat(fixture.users.findById(target.id())).isEmpty();
        assertThat(fixture.sessions.sessions).isEmpty();
        assertThat(fixture.events.published).containsExactly(new UserDeleted(target.id().value()));
    }

    @Test
    void moderatorCannotDeleteAUserOfAnotherCommunityAndNothingChanges() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityB);

        assertDomainCode(() -> deleteService().delete(target.id()), "FORBIDDEN");
        assertThat(fixture.users.findById(target.id())).isPresent();
        assertThat(fixture.events.published).isEmpty();
    }

    @Test
    void nobodyDeletesThemselves() {
        User administrator = fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        assertDomainCode(() -> deleteService().delete(administrator.id()), "SELF_ACTION_FORBIDDEN");
        assertThat(fixture.users.findById(administrator.id())).isPresent();
    }

    @Test
    void deletingTheLastModeratorIsRejectedAndPublishesNothing() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityB));
        User target = fixture.add(UserRole.MODERATOR, fixture.communityA);

        assertDomainCode(() -> deleteService().delete(target.id()), "LAST_MODERATOR");
        assertThat(fixture.users.findById(target.id())).isPresent();
        assertThat(fixture.events.published).isEmpty();
        assertThat(fixture.users.calls).containsExactly("lockCommunity", "countModerators");
    }

    @Test
    void deletingAnAdministratorWhoIsTheLastOneOnceLockedIsRejected() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.ADMINISTRATOR, fixture.communityA);
        fixture.users.administratorsSeenAfterLock = 1;

        assertDomainCode(() -> deleteService().delete(target.id()), "LAST_ADMINISTRATOR");
        assertThat(fixture.users.findById(target.id())).isPresent();
        assertThat(fixture.events.published).isEmpty();
        assertThat(fixture.users.calls).first().isEqualTo("lockAdministrators");
    }

    @Test
    void deletingAnAdministratorWithAnotherOneLeftIsAllowed() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.ADMINISTRATOR, fixture.communityB);

        deleteService().delete(target.id());

        assertThat(fixture.users.findById(target.id())).isEmpty();
    }

    @Test
    void deletingAMissingUserIsNotFound() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        assertThatThrownBy(() -> deleteService().delete(new UserId(UUID.randomUUID())))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void moderatorListsOnlyTheirCommunityOrderedByName() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA, "Zoe"));
        fixture.add(UserRole.REGISTERED, fixture.communityA, "Alba");
        fixture.add(UserRole.REGISTERED, fixture.communityB, "Other");

        UserPage page = listService().list(new ListUsersQuery(Optional.empty(), 0, 50));

        assertThat(page.items()).extracting(user -> user.name().value()).containsExactly("Alba", "Zoe");
        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(50);
    }

    @Test
    void moderatorAskingForAnotherCommunityIsDenied() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));

        assertDomainCode(() -> listService().list(new ListUsersQuery(Optional.of(fixture.communityB.id()), 0, 50)), "FORBIDDEN");
    }

    @Test
    void administratorListsTheRequestedCommunityPaged() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        fixture.add(UserRole.REGISTERED, fixture.communityB, "Alba");
        fixture.add(UserRole.REGISTERED, fixture.communityB, "Berta");
        fixture.add(UserRole.REGISTERED, fixture.communityB, "Carla");

        UserPage page = listService().list(new ListUsersQuery(Optional.of(fixture.communityB.id()), 1, 2));

        assertThat(page.items()).extracting(user -> user.name().value()).containsExactly("Carla");
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void administratorMustChooseACommunity() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        assertThatThrownBy(() -> listService().list(new ListUsersQuery(Optional.empty(), 0, 50)))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> {
                    assertThat(e.field()).isEqualTo("communityId");
                    assertThat(e.code()).isEqualTo("COMMUNITY_REQUIRED");
                });
    }

    @Test
    void administratorAskingForAnUnknownCommunityIsRejected() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        assertThatThrownBy(() -> listService()
                .list(new ListUsersQuery(Optional.of(new CommunityId(UUID.randomUUID())), 0, 50)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void registeredUserCannotListUsers() {
        fixture.actAs(fixture.add(UserRole.REGISTERED, fixture.communityA));

        assertDomainCode(() -> listService().list(new ListUsersQuery(Optional.empty(), 0, 50)), "FORBIDDEN");
    }

    @Test
    void moderatorGetsAUserOfTheirCommunity() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        assertThat(getService().get(target.id()).id()).isEqualTo(target.id());
    }

    @Test
    void moderatorGettingAUserOfAnotherCommunityIsAlwaysDenied() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityB);

        assertDomainCode(() -> getService().get(target.id()), "FORBIDDEN");
    }

    @Test
    void gettingAMissingUserIsNotFound() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));

        assertThatThrownBy(() -> getService().get(new UserId(UUID.randomUUID())))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void registeredUserCannotGetUsers() {
        User registered = fixture.actAs(fixture.add(UserRole.REGISTERED, fixture.communityA));

        assertDomainCode(() -> getService().get(registered.id()), "FORBIDDEN");
    }
}
