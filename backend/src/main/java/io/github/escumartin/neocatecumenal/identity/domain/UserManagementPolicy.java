package io.github.escumartin.neocatecumenal.identity.domain;

public final class UserManagementPolicy {

    private UserManagementPolicy() {
    }

    public static boolean canList(Actor actor, CommunityId community) {
        return isAdministrator(actor) || isModeratorOf(actor, community);
    }

    public static boolean canCreate(Actor actor, UserRole role, CommunityId community) {
        return isAdministrator(actor) || (isModeratorOf(actor, community) && role == UserRole.REGISTERED);
    }

    public static boolean canEditName(Actor actor, User target) {
        return isAdministrator(actor) || managesRegisteredUser(actor, target);
    }

    public static boolean canChangeRoleOrCommunity(Actor actor, User target) {
        return isAdministrator(actor) && !isSelf(actor, target);
    }

    public static boolean canResetPassword(Actor actor, User target) {
        return !isSelf(actor, target) && (isAdministrator(actor) || managesRegisteredUser(actor, target));
    }

    public static boolean canDelete(Actor actor, User target) {
        return canResetPassword(actor, target);
    }

    public static void checkPasswordReset(Actor actor, User target) {
        if (isSelf(actor, target)) {
            throw new DomainException("SELF_ACTION_FORBIDDEN");
        }
        if (!canResetPassword(actor, target)) {
            throw new DomainException("FORBIDDEN");
        }
    }

    public static void checkDeletion(Actor actor, User target, MembershipCounts counts) {
        if (isSelf(actor, target)) {
            throw new DomainException("SELF_ACTION_FORBIDDEN");
        }
        if (!canDelete(actor, target)) {
            throw new DomainException("FORBIDDEN");
        }
        if (target.role() == UserRole.ADMINISTRATOR && counts.administrators() <= 1) {
            throw new DomainException("LAST_ADMINISTRATOR");
        }
        if (target.role() == UserRole.MODERATOR && counts.moderatorsIn(target.communityId()) <= 1) {
            throw new DomainException("LAST_MODERATOR");
        }
    }

    public static void checkRoleOrCommunityChange(Actor actor, User target, UserRole newRole,
            CommunityId newCommunity, MembershipCounts counts) {
        boolean roleChanges = newRole != target.role();
        boolean communityChanges = !newCommunity.equals(target.communityId());
        if (!roleChanges && !communityChanges) {
            return;
        }
        if (isSelf(actor, target)) {
            throw new DomainException("SELF_ACTION_FORBIDDEN");
        }
        if (!canChangeRoleOrCommunity(actor, target)) {
            throw new DomainException("FORBIDDEN");
        }
        if (target.role() == UserRole.ADMINISTRATOR && roleChanges && counts.administrators() <= 1) {
            throw new DomainException("LAST_ADMINISTRATOR");
        }
        boolean leavesModeratorSeat = target.role() == UserRole.MODERATOR
                && (newRole != UserRole.MODERATOR || communityChanges);
        if (leavesModeratorSeat && counts.moderatorsIn(target.communityId()) <= 1) {
            throw new DomainException("LAST_MODERATOR");
        }
    }

    private static boolean isAdministrator(Actor actor) {
        return actor.role() == UserRole.ADMINISTRATOR;
    }

    private static boolean isModeratorOf(Actor actor, CommunityId community) {
        return actor.role() == UserRole.MODERATOR && actor.communityId().equals(community);
    }

    private static boolean managesRegisteredUser(Actor actor, User target) {
        return isModeratorOf(actor, target.communityId()) && target.role() == UserRole.REGISTERED;
    }

    private static boolean isSelf(Actor actor, User target) {
        return actor.id().equals(target.id());
    }
}
