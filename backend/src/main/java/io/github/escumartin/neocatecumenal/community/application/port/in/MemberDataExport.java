package io.github.escumartin.neocatecumenal.community.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MemberDataExport(MemberView member, int communityNumber, List<OwnConsent> consentRecords,
        Instant exportedAt) {

    public MemberDataExport {
        Objects.requireNonNull(member);
        Objects.requireNonNull(exportedAt);
        consentRecords = List.copyOf(consentRecords);
    }
}
