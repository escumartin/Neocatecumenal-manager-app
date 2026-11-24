package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserInvariantsTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private record Counts(int administrators, long moderators) implements MembershipCounts {

        @Override
        public long moderatorsIn(CommunityId communityId) {
            return moderators;
        }
    }

    private static User user(UserRole role, CommunityId community) {
        return User.restore(new UserId(UUID.randomUUID()), new AccessId(123456), new DisplayName("Test Person"),
                role, community, new PasswordHash("{argon2}hash"), false, null, 0L);
    }

    private static Actor actorOf(User user) {
        return new Actor(user.id(), user.role(), user.communityId());
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static final Actor ADMIN = new Actor(new UserId(UUID.randomUUID()), UserRole.ADMINISTRATOR, OWN);

    private static UserChanges changes(String name, UserRole role, CommunityId community) {
        return new UserChanges(new DisplayName(name), role, community);
    }

    @Test
    void administratorUpdateReplacesNameRoleAndCommunity() {
        User user = user(UserRole.REGISTERED, OWN);

        user.update(ADMIN, changes("New Name", UserRole.MODERATOR, OTHER), new Counts(2, 2));

        assertThat(user.name()).isEqualTo(new DisplayName("New Name"));
        assertThat(user.role()).isEqualTo(UserRole.MODERATOR);
        assertThat(user.communityId()).isEqualTo(OTHER);
    }

    @Test
    void updateByAnActorWithoutPermissionLeavesTheUserUntouched() {
        User user = user(UserRole.REGISTERED, OTHER);
        Actor moderatorOfAnotherCommunity = new Actor(new UserId(UUID.randomUUID()), UserRole.MODERATOR, OWN);

        assertCode(() -> user.update(moderatorOfAnotherCommunity,
                changes("New Name", UserRole.REGISTERED, OTHER), new Counts(2, 2)), "FORBIDDEN");

        assertThat(user.name()).isEqualTo(new DisplayName("Test Person"));
    }

    @Test
    void moderatorRenamesARegisteredUserOfTheirCommunity() {
        User user = user(UserRole.REGISTERED, OWN);
        Actor moderator = new Actor(new UserId(UUID.randomUUID()), UserRole.MODERATOR, OWN);

        user.update(moderator, changes("New Name", UserRole.REGISTERED, OWN), new Counts(2, 2));

        assertThat(user.name()).isEqualTo(new DisplayName("New Name"));
    }

    @Test
    void updateChangingTheOwnRoleIsRejectedWithoutMutation() {
        User self = user(UserRole.ADMINISTRATOR, OWN);

        assertCode(() -> self.update(actorOf(self), changes("New Name", UserRole.MODERATOR, OWN), new Counts(2, 2)),
                "SELF_ACTION_FORBIDDEN");

        assertThat(self.role()).isEqualTo(UserRole.ADMINISTRATOR);
        assertThat(self.name()).isEqualTo(new DisplayName("Test Person"));
    }

    @Test
    void updateDemotingTheLastAdministratorIsRejectedWithoutMutation() {
        User target = user(UserRole.ADMINISTRATOR, OWN);

        assertCode(() -> target.update(ADMIN, changes("New Name", UserRole.REGISTERED, OWN), new Counts(1, 0)),
                "LAST_ADMINISTRATOR");

        assertThat(target.role()).isEqualTo(UserRole.ADMINISTRATOR);
    }

    @Test
    void updateMovingTheLastModeratorIsRejectedWithoutMutation() {
        User target = user(UserRole.MODERATOR, OWN);

        assertCode(() -> target.update(ADMIN, changes("New Name", UserRole.MODERATOR, OTHER), new Counts(2, 1)),
                "LAST_MODERATOR");

        assertThat(target.communityId()).isEqualTo(OWN);
    }

    @Test
    void deletingTheLastAdministratorIsRejected() {
        User target = user(UserRole.ADMINISTRATOR, OWN);
        Actor other = actorOf(user(UserRole.ADMINISTRATOR, OWN));

        assertCode(() -> UserManagementPolicy.checkDeletion(other, target, new Counts(1, 0)), "LAST_ADMINISTRATOR");
    }

    @Test
    void deletingAnAdministratorWithAnotherOneLeftIsAllowed() {
        User target = user(UserRole.ADMINISTRATOR, OWN);
        Actor other = actorOf(user(UserRole.ADMINISTRATOR, OWN));

        assertThatCode(() -> UserManagementPolicy.checkDeletion(other, target, new Counts(2, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void deletingTheLastModeratorOfACommunityIsRejected() {
        User target = user(UserRole.MODERATOR, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertCode(() -> UserManagementPolicy.checkDeletion(administrator, target, new Counts(2, 1)),
                "LAST_MODERATOR");
    }

    @Test
    void deletingOneOfTwoModeratorsIsAllowed() {
        User target = user(UserRole.MODERATOR, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertThatCode(() -> UserManagementPolicy.checkDeletion(administrator, target, new Counts(2, 2)))
                .doesNotThrowAnyException();
    }

    @Test
    void deletingARegisteredUserNeverCountsAnyone() {
        User target = user(UserRole.REGISTERED, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));
        MembershipCounts failing = new MembershipCounts() {
            @Override
            public int administrators() {
                throw new AssertionError("administrators counted");
            }

            @Override
            public long moderatorsIn(CommunityId communityId) {
                throw new AssertionError("moderators counted");
            }
        };

        assertThatCode(() -> UserManagementPolicy.checkDeletion(administrator, target, failing))
                .doesNotThrowAnyException();
    }

    @Test
    void anAdministratorCannotDeleteThemselves() {
        User self = user(UserRole.ADMINISTRATOR, OWN);

        assertCode(() -> UserManagementPolicy.checkDeletion(actorOf(self), self, new Counts(3, 3)),
                "SELF_ACTION_FORBIDDEN");
    }

    @Test
    void aModeratorCannotDeleteThemselves() {
        User self = user(UserRole.MODERATOR, OWN);

        assertCode(() -> UserManagementPolicy.checkDeletion(actorOf(self), self, new Counts(3, 3)),
                "SELF_ACTION_FORBIDDEN");
    }

    @Test
    void aModeratorCannotDeleteAnotherCommunitysUser() {
        Actor moderator = actorOf(user(UserRole.MODERATOR, OWN));

        assertCode(() -> UserManagementPolicy.checkDeletion(moderator, user(UserRole.REGISTERED, OTHER),
                new Counts(3, 3)), "FORBIDDEN");
    }

    @Test
    void aUserCannotResetTheirOwnPassword() {
        User self = user(UserRole.ADMINISTRATOR, OWN);

        assertCode(() -> UserManagementPolicy.checkPasswordReset(actorOf(self), self), "SELF_ACTION_FORBIDDEN");
    }

    @Test
    void aModeratorCannotResetAnotherCommunitysPassword() {
        Actor moderator = actorOf(user(UserRole.MODERATOR, OWN));

        assertCode(() -> UserManagementPolicy.checkPasswordReset(moderator, user(UserRole.REGISTERED, OTHER)),
                "FORBIDDEN");
    }

    @Test
    void anAdministratorMayResetAnotherUsersPassword() {
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OWN));

        assertThatCode(() -> UserManagementPolicy.checkPasswordReset(administrator, user(UserRole.MODERATOR, OTHER)))
                .doesNotThrowAnyException();
    }

    @Test
    void anAdministratorCannotChangeTheirOwnRoleOrCommunity() {
        User self = user(UserRole.ADMINISTRATOR, OWN);

        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(actorOf(self), self, UserRole.MODERATOR, OWN,
                new Counts(3, 3)), "SELF_ACTION_FORBIDDEN");
        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(actorOf(self), self, UserRole.ADMINISTRATOR,
                OTHER, new Counts(3, 3)), "SELF_ACTION_FORBIDDEN");
    }

    @Test
    void keepingTheSameRoleAndCommunityIsNotAChange() {
        User self = user(UserRole.ADMINISTRATOR, OWN);

        assertThatCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(actorOf(self), self,
                UserRole.ADMINISTRATOR, OWN, new Counts(1, 0))).doesNotThrowAnyException();
    }

    @Test
    void aModeratorCannotChangeRoleOrCommunity() {
        Actor moderator = actorOf(user(UserRole.MODERATOR, OWN));
        User target = user(UserRole.REGISTERED, OWN);

        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(moderator, target, UserRole.MODERATOR, OWN,
                new Counts(3, 3)), "FORBIDDEN");
        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(moderator, target, UserRole.REGISTERED,
                OTHER, new Counts(3, 3)), "FORBIDDEN");
    }

    @Test
    void demotingTheLastAdministratorIsRejected() {
        User target = user(UserRole.ADMINISTRATOR, OWN);
        Actor other = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(other, target, UserRole.MODERATOR, OWN,
                new Counts(1, 5)), "LAST_ADMINISTRATOR");
    }

    @Test
    void demotingAnAdministratorWithAnotherOneLeftIsAllowed() {
        User target = user(UserRole.ADMINISTRATOR, OWN);
        Actor other = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertThatCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(other, target, UserRole.REGISTERED, OWN,
                new Counts(2, 0))).doesNotThrowAnyException();
    }

    @Test
    void demotingTheLastModeratorIsRejected() {
        User target = user(UserRole.MODERATOR, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(administrator, target, UserRole.REGISTERED,
                OWN, new Counts(2, 1)), "LAST_MODERATOR");
    }

    @Test
    void promotingTheLastModeratorToAdministratorLeavesTheCommunityWithoutModerator() {
        User target = user(UserRole.MODERATOR, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(administrator, target,
                UserRole.ADMINISTRATOR, OWN, new Counts(2, 1)), "LAST_MODERATOR");
    }

    @Test
    void movingTheLastModeratorToAnotherCommunityIsRejected() {
        User target = user(UserRole.MODERATOR, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(administrator, target, UserRole.MODERATOR,
                OTHER, new Counts(2, 1)), "LAST_MODERATOR");
    }

    @Test
    void movingOneOfTwoModeratorsIsAllowed() {
        User target = user(UserRole.MODERATOR, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertThatCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(administrator, target, UserRole.MODERATOR,
                OTHER, new Counts(2, 2))).doesNotThrowAnyException();
    }

    @Test
    void promotingARegisteredUserToModeratorNeverBlocks() {
        User target = user(UserRole.REGISTERED, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OTHER));

        assertThatCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(administrator, target,
                UserRole.MODERATOR, OWN, new Counts(1, 0))).doesNotThrowAnyException();
    }

    @Test
    void anAdministratorWhoIsAlsoTheLastAdministratorCanStillMoveAnotherUser() {
        User target = user(UserRole.REGISTERED, OWN);
        Actor administrator = actorOf(user(UserRole.ADMINISTRATOR, OWN));

        assertThatCode(() -> UserManagementPolicy.checkRoleOrCommunityChange(administrator, target,
                UserRole.REGISTERED, OTHER, new Counts(1, 0))).doesNotThrowAnyException();
    }
}
