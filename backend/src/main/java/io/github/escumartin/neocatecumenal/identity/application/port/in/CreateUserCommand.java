package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;

public record CreateUserCommand(DisplayName name, UserRole role, CommunityId communityId) {
}
