package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Member;
import java.util.Objects;

public record MemberView(Member member, boolean responsible) {

    public MemberView {
        Objects.requireNonNull(member);
    }
}
