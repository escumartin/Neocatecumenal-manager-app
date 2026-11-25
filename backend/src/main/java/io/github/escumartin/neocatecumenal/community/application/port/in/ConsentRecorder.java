package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.RecorderRole;
import java.util.Objects;
import java.util.Optional;

public record ConsentRecorder(RecorderRole role, Optional<String> name, boolean deleted) {

    public ConsentRecorder {
        Objects.requireNonNull(role);
        Objects.requireNonNull(name);
    }
}
