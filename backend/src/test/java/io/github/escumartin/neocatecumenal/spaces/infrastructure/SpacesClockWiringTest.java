package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.escumartin.neocatecumenal.ClockConfig;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingCounter;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomOccupancyQuery;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.config.SpacesConfig;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.config.SpacesServiceClock;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpacesClockWiringTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ClockConfig.class, SpacesConfig.class)
                    .withBean(RoomRepository.class, () -> mock(RoomRepository.class))
                    .withBean(RoomOccupancyQuery.class, () -> mock(RoomOccupancyQuery.class))
                    .withBean(BookingCounter.class, () -> mock(BookingCounter.class))
                    .withBean(BookingRequestRepository.class, () -> mock(BookingRequestRepository.class));

    @Test
    void contextKeepsASingleClockBean() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }

    @Test
    void serviceClockUsesTheMadridZone() {
        runner.run(context -> assertThat(context.getBean(SpacesServiceClock.class).clock().getZone())
                .isEqualTo(ZoneId.of("Europe/Madrid")));
    }
}
