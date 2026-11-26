package io.github.escumartin.neocatecumenal.community.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.community.application.port.in.PurgeExpiredErasures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ErasurePurgeJobTest {

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
    void aScheduledRunInvokesThePurgeOnce() {
        int[] runs = {0};

        new ErasurePurgeJob(() -> runs[0]++).purgeExpired();

        assertThat(runs[0]).isEqualTo(1);
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void aFailingPurgeDoesNotEscapeTheScheduledRunAndIsLoggedWithItsCause() {
        PurgeExpiredErasures failing = () -> {
            throw new IllegalStateException("database unavailable");
        };

        assertThatCode(() -> new ErasurePurgeJob(failing).purgeExpired()).doesNotThrowAnyException();

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Fallo al purgar el registro de supresiones caducado");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("database unavailable");
        });
    }
}
