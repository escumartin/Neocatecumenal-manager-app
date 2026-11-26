package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.identity.application.port.in.PurgeExpiredAccessData;
import io.github.escumartin.neocatecumenal.identity.application.port.in.PurgeExpiredErasures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AccessDataPurgeJobTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger rootLogger;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void aFailingPurgeDoesNotEscapeTheScheduledRunAndIsLoggedWithItsCause() {
        PurgeExpiredAccessData failing = () -> {
            throw new IllegalStateException("database unavailable");
        };

        assertThatCode(() -> new AccessDataPurgeJob(failing, () -> { }).purgeExpired()).doesNotThrowAnyException();

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Fallo al purgar datos de acceso caducados");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("database unavailable");
        });
    }

    @Test
    void aSuccessfulPurgeRunsTheUseCaseOnceAndLogsNoError() {
        int[] runs = {0};

        new AccessDataPurgeJob(() -> runs[0]++, () -> { }).purgeExpired();

        assertThat(runs[0]).isEqualTo(1);
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void aSuccessfulRunPurgesTheExpiredErasuresOnce() {
        int[] runs = {0};
        PurgeExpiredErasures erasures = () -> runs[0]++;

        new AccessDataPurgeJob(() -> { }, erasures).purgeExpired();

        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void aFailingAccessDataPurgeStillPurgesTheExpiredErasures() {
        int[] runs = {0};
        PurgeExpiredAccessData failing = () -> {
            throw new IllegalStateException("database unavailable");
        };

        new AccessDataPurgeJob(failing, () -> runs[0]++).purgeExpired();

        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void aFailingErasurePurgeDoesNotEscapeTheScheduledRunAndIsLogged() {
        PurgeExpiredErasures failing = () -> {
            throw new IllegalStateException("database unavailable");
        };

        assertThatCode(() -> new AccessDataPurgeJob(() -> { }, failing).purgeExpired()).doesNotThrowAnyException();

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Fallo al purgar el registro de supresiones caducado");
        });
    }
}
