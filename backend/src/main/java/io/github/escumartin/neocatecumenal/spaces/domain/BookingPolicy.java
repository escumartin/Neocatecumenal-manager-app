package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.Optional;

public final class BookingPolicy {

    private BookingPolicy() {
    }

    public static CommunityId actingCommunity(Actor actor, CommunityId requested) {
        return switch (actor.role()) {
            case REGISTERED -> throw new SpacesForbidden();
            case MODERATOR -> moderatorCommunity(actor, requested);
            case ADMINISTRATOR -> administratorCommunity(requested);
        };
    }

    private static CommunityId moderatorCommunity(Actor actor, CommunityId requested) {
        if (requested == null || requested.equals(actor.ownCommunityId())) {
            return actor.ownCommunityId();
        }
        throw new SpacesForbidden();
    }

    private static CommunityId administratorCommunity(CommunityId requested) {
        if (requested == null) {
            throw new SpacesFieldInvalid("communityId", "COMMUNITY_REQUIRED");
        }
        return requested;
    }

    public static Optional<CommunityId> visibleCommunity(Actor actor, CommunityId requested) {
        return switch (actor.role()) {
            case REGISTERED -> throw new SpacesForbidden();
            case MODERATOR -> Optional.of(moderatorCommunity(actor, requested));
            case ADMINISTRATOR -> Optional.ofNullable(requested);
        };
    }

    public static void requireAccessTo(Actor actor, CommunityId owner) {
        switch (actor.role()) {
            case REGISTERED -> throw new SpacesForbidden();
            case MODERATOR -> moderatorCommunity(actor, owner);
            case ADMINISTRATOR -> {
            }
        }
    }

    public static void requireStaff(Actor actor) {
        if (actor.role() == ActorRole.REGISTERED) {
            throw new SpacesForbidden();
        }
    }

    public static boolean canSee(Actor actor, CommunityId owner) {
        return switch (actor.role()) {
            case REGISTERED -> false;
            case MODERATOR -> owner.equals(actor.ownCommunityId());
            case ADMINISTRATOR -> true;
        };
    }

    public static void requireAdministrator(Actor actor) {
        if (actor.role() != ActorRole.ADMINISTRATOR) {
            throw new SpacesForbidden();
        }
    }
}
