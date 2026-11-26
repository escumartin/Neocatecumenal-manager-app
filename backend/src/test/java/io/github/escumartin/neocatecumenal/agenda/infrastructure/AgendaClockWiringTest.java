package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.escumartin.neocatecumenal.ClockConfig;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.CommunityLock;
import io.github.escumartin.neocatecumenal.agenda.application.port.out.EventRepository;
import io.github.escumartin.neocatecumenal.agenda.infrastructure.config.AgendaConfig;
import io.github.escumartin.neocatecumenal.agenda.infrastructure.config.AgendaServiceClock;
import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgendaClockWiringTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ClockConfig.class, AgendaConfig.class)
                    .withBean(SpacesApi.class, () -> mock(SpacesApi.class))
                    .withBean(EventRepository.class, () -> mock(EventRepository.class))
                    .withBean(CommunityLock.class, () -> mock(CommunityLock.class));

    @Test
    void contextKeepsASingleClockBean() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }

    @Test
    void serviceClockUsesTheMadridZone() {
        runner.run(context -> assertThat(context.getBean(AgendaServiceClock.class).clock().getZone())
                .isEqualTo(ZoneId.of("Europe/Madrid")));
    }
}
