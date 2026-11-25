package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;

public record OnBehalfConsent(ConsentChannel channel, boolean noticeGiven) {

    public OnBehalfConsent {
        Objects.requireNonNull(channel);
        if (channel == ConsentChannel.APP) {
            throw new IllegalArgumentException("channel");
        }
    }
}
