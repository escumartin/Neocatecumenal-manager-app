package io.github.escumartin.neocatecumenal.agenda.domain;

public final class EventPolicy {

    private EventPolicy() {
    }

    public static CommunityId actingCommunity(Actor actor, CommunityId requested) {
        return switch (actor.role()) {
            case REGISTERED -> throw new AgendaForbidden();
            case MODERATOR -> moderatorCommunity(actor, requested);
            case ADMINISTRATOR -> administratorCommunity(requested);
        };
    }

    public static void requireAccessTo(Actor actor, CommunityId owner) {
        switch (actor.role()) {
            case REGISTERED -> throw new AgendaForbidden();
            case MODERATOR -> moderatorCommunity(actor, owner);
            case ADMINISTRATOR -> {
            }
        }
    }

    public static void requireStaff(Actor actor) {
        if (actor.role() == ActorRole.REGISTERED) {
            throw new AgendaForbidden();
        }
    }

    public static boolean canSee(Actor actor, CommunityId owner) {
        return switch (actor.role()) {
            case REGISTERED -> false;
            case MODERATOR -> owner.equals(actor.ownCommunityId());
            case ADMINISTRATOR -> true;
        };
    }

    private static CommunityId moderatorCommunity(Actor actor, CommunityId requested) {
        if (requested == null || requested.equals(actor.ownCommunityId())) {
            return actor.ownCommunityId();
        }
        throw new AgendaForbidden();
    }

    private static CommunityId administratorCommunity(CommunityId requested) {
        if (requested == null) {
            throw new AgendaFieldInvalid("communityId", "COMMUNITY_REQUIRED");
        }
        return requested;
    }
}
