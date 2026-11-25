package io.github.escumartin.neocatecumenal.agenda.application.service;

import io.github.escumartin.neocatecumenal.agenda.AgendaApi;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AgendaApiAdapter implements AgendaApi {

    private final EventRepository events;

    public AgendaApiAdapter(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEventsFor(UUID communityId) {
        return events.existsByCommunity(new CommunityId(communityId));
    }
}
