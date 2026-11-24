package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;

public record LoginCommand(AccessId accessId, PlainPassword password, String clientIp) {
}
