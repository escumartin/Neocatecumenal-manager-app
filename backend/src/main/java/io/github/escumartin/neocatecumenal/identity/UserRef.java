package io.github.escumartin.neocatecumenal.identity;

import java.util.UUID;

public record UserRef(UUID id, String name, Role role, UUID communityId) {
}
