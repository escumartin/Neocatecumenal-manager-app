package io.github.escumartin.neocatecumenal.community.application.port.out;

import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import java.time.Instant;

public interface ErasureRecords {

    void recordMember(MemberId memberId, Instant erasedAt);

    void recordContactField(MemberId memberId, ContactField field, Instant erasedAt);

    int purgeBefore(Instant cutoff);
}
