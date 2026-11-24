package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;

public interface PasswordHasher {

    PasswordHash hash(PlainPassword password);

    boolean matches(PlainPassword password, PasswordHash hash);

    boolean matchesDummy(PlainPassword password);
}
