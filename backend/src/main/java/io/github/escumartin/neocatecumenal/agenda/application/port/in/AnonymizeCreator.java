package io.github.escumartin.neocatecumenal.agenda.application.port.in;

import io.github.escumartin.neocatecumenal.agenda.domain.UserId;

public interface AnonymizeCreator {

    void anonymize(UserId userId);
}
