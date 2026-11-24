package io.github.escumartin.neocatecumenal.identity.application.port.in;

import java.util.Optional;

public record BootstrapOutcome(int createdCommunities, Optional<CreatedAdministrator> administrator) {
}
