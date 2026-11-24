package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;

public interface ResetUserPassword {

    PlainPassword reset(UserId userId);
}
