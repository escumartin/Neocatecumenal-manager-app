package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import java.util.Objects;

public record OwnConsent(ConsentRecord record, ConsentRecorder recordedBy) {

    public OwnConsent {
        Objects.requireNonNull(record);
        Objects.requireNonNull(recordedBy);
    }
}
