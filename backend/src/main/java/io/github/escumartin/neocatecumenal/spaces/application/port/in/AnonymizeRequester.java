package io.github.escumartin.neocatecumenal.spaces.application.port.in;

import io.github.escumartin.neocatecumenal.spaces.domain.UserId;

public interface AnonymizeRequester {

    void anonymize(UserId userId);
}
