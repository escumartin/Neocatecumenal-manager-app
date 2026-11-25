package io.github.escumartin.neocatecumenal.identity;

import java.util.UUID;

public record CommunityRef(UUID id, int number, boolean active) {
}
