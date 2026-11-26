package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.ErasureRecords;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class InMemoryErasures implements ErasureRecords {

    record Entry(MemberId memberId, ContactField field, Instant erasedAt) {
    }

    final List<Entry> entries = new ArrayList<>();

    @Override
    public void recordMember(MemberId memberId, Instant erasedAt) {
        entries.add(new Entry(memberId, null, erasedAt));
    }

    @Override
    public void recordContactField(MemberId memberId, ContactField field, Instant erasedAt) {
        entries.add(new Entry(memberId, field, erasedAt));
    }

    @Override
    public int purgeBefore(Instant cutoff) {
        int before = entries.size();
        entries.removeIf(entry -> entry.erasedAt().isBefore(cutoff));
        return before - entries.size();
    }
}
