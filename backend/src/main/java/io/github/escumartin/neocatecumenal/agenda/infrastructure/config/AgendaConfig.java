package io.github.escumartin.neocatecumenal.agenda.infrastructure.config;

import io.github.escumartin.neocatecumenal.agenda.AgendaApi;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.AnonymizeCreator;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.CreateEvent;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.GetEvent;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.PurgeExpiredEvents;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.application.service.AgendaApiAdapter;
import io.github.escumartin.neocatecumenal.agenda.application.service.AnonymizeCreatorService;
import io.github.escumartin.neocatecumenal.agenda.application.service.CreateEventService;
import io.github.escumartin.neocatecumenal.agenda.application.service.GetEventService;
import io.github.escumartin.neocatecumenal.agenda.application.service.PurgeExpiredEventsService;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgendaConfig {

    @Bean
    AgendaApi agendaApi(EventRepository events) {
        return new AgendaApiAdapter(events);
    }

    @Bean
    AgendaServiceClock agendaServiceClock(Clock clock) {
        return new AgendaServiceClock(clock.withZone(ZoneId.of("Europe/Madrid")));
    }

    @Bean
    CreateEvent createEvent(SpacesApi spaces, EventRepository events, AgendaServiceClock clock) {
        return new CreateEventService(spaces, events, clock.clock());
    }

    @Bean
    GetEvent getEvent(SpacesApi spaces, EventRepository events) {
        return new GetEventService(spaces, events);
    }

    @Bean
    AnonymizeCreator anonymizeCreator(EventRepository events) {
        return new AnonymizeCreatorService(events);
    }

    @Bean
    PurgeExpiredEvents purgeExpiredEvents(EventRepository events, AgendaServiceClock clock) {
        return new PurgeExpiredEventsService(events, clock.clock());
    }
}
