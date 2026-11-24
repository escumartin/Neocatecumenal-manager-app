package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

class UserManagementPolicyTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static User user(UserRole role, CommunityId community) {
        return User.restore(new UserId(UUID.randomUUID()), new AccessId(123456), new DisplayName("Test Person"),
                role, community, new PasswordHash("{argon2}hash"), false, null, 0L);
    }

    private static Actor actorOf(User user) {
        return new Actor(user.id(), user.role(), user.communityId());
    }

    private static void assertMatrix(BiPredicate<Actor, User> action, boolean administratorOverOther,
            boolean moderatorOverOwnRegistered) {
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OWN));
        Actor moderator = actorOf(user(UserRole.MODERATOR, OWN));
        Actor registered = actorOf(user(UserRole.REGISTERED, OWN));

        assertThat(action.test(administrator, user(UserRole.REGISTERED, OTHER))).isEqualTo(administratorOverOther);
        assertThat(action.test(administrator, user(UserRole.MODERATOR, OTHER))).isEqualTo(administratorOverOther);
        assertThat(action.test(administrator, user(UserRole.ADMINISTRATOR, OTHER))).isEqualTo(administratorOverOther);
        assertThat(action.test(moderator, user(UserRole.REGISTERED, OWN))).isEqualTo(moderatorOverOwnRegistered);
        assertThat(action.test(moderator, user(UserRole.REGISTERED, OTHER))).isFalse();
        assertThat(action.test(moderator, user(UserRole.MODERATOR, OWN))).isFalse();
        assertThat(action.test(moderator, user(UserRole.ADMINISTRATOR, OWN))).isFalse();
        assertThat(action.test(registered, user(UserRole.REGISTERED, OWN))).isFalse();
        assertThat(action.test(registered, user(UserRole.MODERATOR, OWN))).isFalse();
    }

    @Test
    void administratorListsUsersOfAnyCommunity() {
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OWN));

        assertThat(UserManagementPolicy.canList(administrator, OWN)).isTrue();
        assertThat(UserManagementPolicy.canList(administrator, OTHER)).isTrue();
    }

    @Test
    void moderatorListsOnlyTheirOwnCommunity() {
        Actor moderator = actorOf(user(UserRole.MODERATOR, OWN));

        assertThat(UserManagementPolicy.canList(moderator, OWN)).isTrue();
        assertThat(UserManagementPolicy.canList(moderator, OTHER)).isFalse();
    }

    @Test
    void registeredUserCannotListUsers() {
        Actor registered = actorOf(user(UserRole.REGISTERED, OWN));

        assertThat(UserManagementPolicy.canList(registered, OWN)).isFalse();
    }

    @Test
    void administratorCreatesAnyRoleInAnyCommunity() {
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OWN));

        for (UserRole role : UserRole.values()) {
            assertThat(UserManagementPolicy.canCreate(administrator, role, OWN)).isTrue();
            assertThat(UserManagementPolicy.canCreate(administrator, role, OTHER)).isTrue();
        }
    }

    @Test
    void moderatorCreatesOnlyRegisteredUsersInTheirCommunity() {
        Actor moderator = actorOf(user(UserRole.MODERATOR, OWN));

        assertThat(UserManagementPolicy.canCreate(moderator, UserRole.REGISTERED, OWN)).isTrue();
        assertThat(UserManagementPolicy.canCreate(moderator, UserRole.REGISTERED, OTHER)).isFalse();
        assertThat(UserManagementPolicy.canCreate(moderator, UserRole.MODERATOR, OWN)).isFalse();
        assertThat(UserManagementPolicy.canCreate(moderator, UserRole.ADMINISTRATOR, OWN)).isFalse();
    }

    @Test
    void registeredUserCreatesNobody() {
        Actor registered = actorOf(user(UserRole.REGISTERED, OWN));

        for (UserRole role : UserRole.values()) {
            assertThat(UserManagementPolicy.canCreate(registered, role, OWN)).isFalse();
        }
    }

    @Test
    void editNameFollowsTheMatrix() {
        assertMatrix(UserManagementPolicy::canEditName, true, true);
    }

    @Test
    void administratorEditsTheirOwnName() {
        User administrator = user(UserRole.ADMINISTRATOR, OWN);

        assertThat(UserManagementPolicy.canEditName(actorOf(administrator), administrator)).isTrue();
    }

    @Test
    void resetPasswordFollowsTheMatrix() {
        assertMatrix(UserManagementPolicy::canResetPassword, true, true);
    }

    @Test
    void deleteFollowsTheMatrix() {
        assertMatrix(UserManagementPolicy::canDelete, true, true);
    }

    @Test
    void changeRoleOrCommunityIsOnlyForAdministrators() {
        assertMatrix(UserManagementPolicy::canChangeRoleOrCommunity, true, false);
    }

    @Test
    void administratorCannotResetDeleteOrChangeTheirOwnAccount() {
        User administrator = user(UserRole.ADMINISTRATOR, OWN);
        Actor actor = actorOf(administrator);

        assertThat(UserManagementPolicy.canResetPassword(actor, administrator)).isFalse();
        assertThat(UserManagementPolicy.canDelete(actor, administrator)).isFalse();
        assertThat(UserManagementPolicy.canChangeRoleOrCommunity(actor, administrator)).isFalse();
    }

    @Test
    void moderatorOfAnotherCommunityIsAlwaysDenied() {
        Actor moderator = actorOf(user(UserRole.MODERATOR, OTHER));
        User target = user(UserRole.REGISTERED, OWN);

        assertThat(UserManagementPolicy.canEditName(moderator, target)).isFalse();
        assertThat(UserManagementPolicy.canResetPassword(moderator, target)).isFalse();
        assertThat(UserManagementPolicy.canDelete(moderator, target)).isFalse();
        assertThat(UserManagementPolicy.canChangeRoleOrCommunity(moderator, target)).isFalse();
    }
}
