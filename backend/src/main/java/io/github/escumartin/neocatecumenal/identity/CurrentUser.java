package io.github.escumartin.neocatecumenal.identity;

import java.util.UUID;

public record CurrentUser(UUID userId, int accessId, String name, Role role, UUID communityId) {
}
