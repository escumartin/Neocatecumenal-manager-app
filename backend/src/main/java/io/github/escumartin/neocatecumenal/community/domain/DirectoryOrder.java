package io.github.escumartin.neocatecumenal.community.domain;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DirectoryOrder {

    private DirectoryOrder() {
    }

    public static List<Member> sorted(List<Member> members) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        collator.setStrength(Collator.PRIMARY);
        Comparator<Member> order = Comparator
                .comparing((Member member) -> member.name().surnames().orElse(member.name().givenName()), collator)
                .thenComparing(member -> member.name().givenName(), collator)
                .thenComparing(member -> member.id().value());
        return members.stream().sorted(order).toList();
    }
}
