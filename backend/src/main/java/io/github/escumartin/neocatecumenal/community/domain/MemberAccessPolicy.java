package io.github.escumartin.neocatecumenal.community.domain;

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

    private static void requireAdministrator(Actor actor) {
        if (actor.role() != ActorRole.ADMINISTRATOR) {
            throw new CommunityForbidden();
        }
    }
}
