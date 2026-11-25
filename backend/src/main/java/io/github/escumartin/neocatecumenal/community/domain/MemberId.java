package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;
import java.util.UUID;

public record MemberId(UUID value) {

    public MemberId {
        Objects.requireNonNull(value);
    }
}
