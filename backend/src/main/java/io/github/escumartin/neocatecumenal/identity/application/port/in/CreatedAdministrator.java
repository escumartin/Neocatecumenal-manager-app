package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;

public record CreatedAdministrator(AccessId accessId, PlainPassword initialPassword) {
}
