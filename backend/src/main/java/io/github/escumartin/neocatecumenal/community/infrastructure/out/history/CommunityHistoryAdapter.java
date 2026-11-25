package io.github.escumartin.neocatecumenal.community.infrastructure.out.history;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityHistory;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.AgendaApi;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import org.springframework.stereotype.Component;

@Component
public class CommunityHistoryAdapter implements CommunityHistory {

    private final SpacesApi spaces;
    private final AgendaApi agenda;

    CommunityHistoryAdapter(SpacesApi spaces, AgendaApi agenda) {
        this.spaces = spaces;
        this.agenda = agenda;
    }

    @Override
    public boolean hasHistory(CommunityId communityId) {
        return spaces.hasBookingRequestsFor(communityId.value()) || agenda.hasEventsFor(communityId.value());
    }
}
