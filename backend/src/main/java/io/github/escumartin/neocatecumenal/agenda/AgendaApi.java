package io.github.escumartin.neocatecumenal.agenda;

import java.util.UUID;

public interface AgendaApi {

    boolean hasEventsFor(UUID communityId);
}
