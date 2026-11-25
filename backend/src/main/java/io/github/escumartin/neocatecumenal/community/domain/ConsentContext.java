package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;

public record ConsentContext(UserId recordedBy, RecorderRole role, ConsentChannel channel, boolean noticeGiven) {

    public ConsentContext {
        Objects.requireNonNull(recordedBy);
        Objects.requireNonNull(role);
        Objects.requireNonNull(channel);
        if ((channel == ConsentChannel.APP) != (role == RecorderRole.MEMBER)) {
            throw new IllegalArgumentException("channel");
        }
    }

    public static ConsentContext byMember(UserId member) {
        return new ConsentContext(member, RecorderRole.MEMBER, ConsentChannel.APP, true);
    }

    public static ConsentContext onBehalf(Actor actor, OnBehalfConsent consent) {
        RecorderRole role = switch (actor.role()) {
            case MODERATOR -> RecorderRole.MODERATOR;
            case ADMINISTRATOR -> RecorderRole.ADMINISTRATOR;
            case REGISTERED -> throw new CommunityForbidden();
        };
        return new ConsentContext(actor.id(), role, consent.channel(), consent.noticeGiven());
    }

    boolean needsNotice() {
        return channel != ConsentChannel.APP;
    }
}
