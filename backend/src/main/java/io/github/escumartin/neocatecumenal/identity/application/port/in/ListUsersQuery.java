package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import java.util.Optional;

public record ListUsersQuery(Optional<CommunityId> communityId, int page, int size) {
}
