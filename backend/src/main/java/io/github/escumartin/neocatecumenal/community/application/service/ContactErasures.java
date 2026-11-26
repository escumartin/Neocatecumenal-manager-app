package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.out.ErasureRecords;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import java.time.Instant;
import java.util.Set;

final class ContactErasures {

    private ContactErasures() {
    }

    static Set<ContactField> valuedFieldsOf(Member member) {
        return Set.copyOf(member.contact().keySet());
    }

    static void recordWithdrawn(ErasureRecords erasures, Member member, Set<ContactField> valuedBefore,
            Instant erasedAt) {
        valuedBefore.stream()
                .filter(field -> !member.contact().containsKey(field))
                .sorted()
                .forEach(field -> erasures.recordContactField(member.id(), field, erasedAt));
    }
}
