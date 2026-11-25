package io.github.escumartin.neocatecumenal.community.application.port.in;

import java.util.List;
import java.util.Objects;

public record OwnMemberView(MemberView member, List<OwnConsent> currentConsents) {

    public OwnMemberView {
        Objects.requireNonNull(member);
        currentConsents = List.copyOf(currentConsents);
    }
}
