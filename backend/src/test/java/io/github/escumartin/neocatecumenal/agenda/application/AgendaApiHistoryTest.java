package io.github.escumartin.neocatecumenal.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.application.service.AgendaApiAdapter;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgendaApiHistoryTest {

    private final EventRepository events = mock(EventRepository.class);
    private final AgendaApiAdapter api = new AgendaApiAdapter(events);
    private final UUID community = UUID.randomUUID();

    @Test
    void reportsHistoryWhenTheCommunityHasEvents() {
        when(events.existsByCommunity(new CommunityId(community))).thenReturn(true);

        assertThat(api.hasEventsFor(community)).isTrue();
    }

    @Test
    void reportsNoHistoryWhenTheCommunityHasNoEvents() {
        when(events.existsByCommunity(new CommunityId(community))).thenReturn(false);

        assertThat(api.hasEventsFor(community)).isFalse();
    }

    @Test
    void asksTheRepositoryAboutTheGivenCommunityOnly() {
        api.hasEventsFor(community);

        verify(events).existsByCommunity(new CommunityId(community));
    }
}
