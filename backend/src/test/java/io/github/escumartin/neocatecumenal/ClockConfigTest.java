package io.github.escumartin.neocatecumenal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class ClockConfigTest {

    @Test
    void exposesASingleUtcClockBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClockConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
                });
    }

    @Test
    void identityConfigDoesNotDeclareAClockBean() throws ClassNotFoundException {
        Class<?> identityConfig =
                Class.forName("io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityConfig");
        boolean declaresClock = Arrays.stream(identityConfig.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(Method::getReturnType)
                .anyMatch(Clock.class::isAssignableFrom);

        assertThat(declaresClock).isFalse();
    }
}
