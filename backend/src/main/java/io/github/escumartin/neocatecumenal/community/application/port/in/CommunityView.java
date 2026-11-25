package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import java.util.Objects;
import java.util.Optional;

public record CommunityView(Community community, Optional<Member> responsible, boolean responsibleIsModerator) {

    public CommunityView {
        Objects.requireNonNull(community);
        Objects.requireNonNull(responsible);
    }
}
