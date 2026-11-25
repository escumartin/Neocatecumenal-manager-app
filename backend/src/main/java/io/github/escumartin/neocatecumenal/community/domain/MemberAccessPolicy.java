package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Optional;

public final class MemberAccessPolicy {

    private MemberAccessPolicy() {
    }

    public static void requireCanManageCommunities(Actor actor) {
        requireAdministrator(actor);
    }

    public static void requireCanDesignateResponsible(Actor actor) {
        requireAdministrator(actor);
    }

    public static void requireCanViewCommunityFile(Actor actor, CommunityId community) {
        boolean ownModerator = actor.role() == ActorRole.MODERATOR && actor.communityId().equals(community);
        if (actor.role() != ActorRole.ADMINISTRATOR && !ownModerator) {
            throw new CommunityForbidden();
        }
    }

    public static void requireCanManageMembers(Actor actor) {
        if (actor.role() == ActorRole.REGISTERED) {
            throw new CommunityForbidden();
        }
    }

    public static void requireCanManageMembers(Actor actor, CommunityId community) {
        if (!canManageMembers(actor, community)) {
            throw new CommunityForbidden();
        }
    }

    public static boolean canManageMembers(Actor actor, CommunityId community) {
        return actor.role() == ActorRole.ADMINISTRATOR
                || (actor.role() == ActorRole.MODERATOR && actor.communityId().equals(community));
    }

    public static void requireCanViewDirectory(Actor actor, CommunityId community) {
        if (!canViewMember(actor, community)) {
            throw new CommunityForbidden();
        }
    }

    public static boolean canViewMember(Actor actor, CommunityId community) {
        return actor.role() == ActorRole.ADMINISTRATOR || actor.communityId().equals(community);
    }

    public static void requireDirectoryAvailable(Actor actor, CommunityStatus status) {
        if (actor.role() != ActorRole.ADMINISTRATOR && status == CommunityStatus.DEACTIVATED) {
            throw new CommunityDomainException("COMMUNITY_DEACTIVATED");
        }
    }

    public static void requireCanLink(Actor actor, CommunityId memberCommunity, LinkedUser user) {
        if (actor.role() == ActorRole.ADMINISTRATOR) {
            return;
        }
        boolean ownCommunity = actor.communityId().equals(memberCommunity)
                && actor.communityId().equals(user.communityId());
        if (actor.role() != ActorRole.MODERATOR || !ownCommunity || user.role() != ActorRole.REGISTERED) {
            throw new CommunityForbidden();
        }
    }

    public static void requireCanUnlink(Actor actor, CommunityId memberCommunity, Optional<LinkedUser> linked) {
        if (actor.role() == ActorRole.ADMINISTRATOR) {
            return;
        }
        boolean allowed = actor.role() == ActorRole.MODERATOR
                && actor.communityId().equals(memberCommunity)
                && linked.filter(user -> user.role() == ActorRole.REGISTERED && !user.id().equals(actor.id()))
                        .isPresent();
        if (!allowed) {
            throw new CommunityForbidden();
        }
    }

    public static void requireCanDelete(Actor actor, CommunityId memberCommunity, Optional<LinkedUser> linked) {
        if (actor.role() == ActorRole.ADMINISTRATOR) {
            return;
        }
        boolean allowed = actor.role() == ActorRole.MODERATOR
                && actor.communityId().equals(memberCommunity)
                && linked.map(user -> user.role() == ActorRole.REGISTERED && !user.id().equals(actor.id()))
                        .orElse(true);
        if (!allowed) {
            throw new CommunityForbidden();
        }
    }

    public static void requireCanTransfer(Actor actor) {
        requireAdministrator(actor);
    }

    private static void requireAdministrator(Actor actor) {
        if (actor.role() != ActorRole.ADMINISTRATOR) {
            throw new CommunityForbidden();
        }
    }
}
