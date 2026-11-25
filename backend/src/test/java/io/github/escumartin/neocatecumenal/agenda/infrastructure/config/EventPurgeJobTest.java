package io.github.escumartin.neocatecumenal.agenda.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.agenda.application.port.in.PurgeExpiredEvents;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

class EventPurgeJobTest {

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
    void aSuccessfulRunPurgesOnceAndLogsNoError() {
        int[] runs = {0};

        new EventPurgeJob(() -> runs[0]++).purgeExpired();

        assertThat(runs[0]).isEqualTo(1);
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void aFailingRunDoesNotEscapeAndIsLoggedWithItsCause() {
        PurgeExpiredEvents failing = () -> {
            throw new IllegalStateException("database unavailable");
        };

        assertThatCode(() -> new EventPurgeJob(failing).purgeExpired()).doesNotThrowAnyException();

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Fallo al purgar eventos caducados");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("database unavailable");
        });
    }

    @Test
    void isScheduledOnceADayInTheMadridZone() throws NoSuchMethodException {
        Method method = EventPurgeJob.class.getDeclaredMethod("purgeExpired");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("Europe/Madrid");
        CronExpression cron = CronExpression.parse(scheduled.cron());
        LocalDateTime first = cron.next(LocalDateTime.of(2030, 5, 10, 12, 0));
        LocalDateTime second = cron.next(first);
        assertThat(Duration.between(first, second)).isEqualTo(Duration.ofDays(1));
    }
}
