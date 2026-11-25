package io.github.escumartin.neocatecumenal.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConsentRecord(UUID id, ContactField field, ConsentDecision decision, ConsentChannel channel,
        UserId recordedBy, RecorderRole recorderRole, boolean noticeGiven, Instant recordedAt) {

    public ConsentRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(field);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(recordedBy);
        Objects.requireNonNull(recorderRole);
        Objects.requireNonNull(recordedAt);
        if ((channel == ConsentChannel.APP) != (recorderRole == RecorderRole.MEMBER)) {
            throw new IllegalArgumentException("channel");
        }
        if (channel != ConsentChannel.APP && !noticeGiven) {
            throw new IllegalArgumentException("noticeGiven");
        }
    }
}
