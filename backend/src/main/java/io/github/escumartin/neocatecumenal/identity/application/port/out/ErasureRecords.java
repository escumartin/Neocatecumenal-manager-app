package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.time.Instant;

public interface ErasureRecords {

    void recordUser(UserId userId, Instant erasedAt);

    int purgeBefore(Instant cutoff);
}
