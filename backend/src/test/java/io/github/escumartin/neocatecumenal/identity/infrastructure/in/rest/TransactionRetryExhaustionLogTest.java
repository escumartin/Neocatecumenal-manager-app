package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TransactionRetryExhaustionLogTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(TransactionRetry.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void exhaustedRetriesLogAWarningWithTheNumberOfCallsAndRethrow() {
        TransactionRetry retry = new TransactionRetry();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new StaleLockOrderException();
        })).isInstanceOf(StaleLockOrderException.class);

        assertThat(calls).hasValue(3);
        assertThat(appender.list).filteredOn(event -> "Reintentos de transacción agotados"
                .equals(event.getFormattedMessage())).singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                            .containsExactly(org.assertj.core.groups.Tuple.tuple("calls", 3));
                });
    }

    @Test
    void aSuccessfulRetryLogsNoExhaustion() {
        TransactionRetry retry = new TransactionRetry();
        AtomicInteger calls = new AtomicInteger();

        retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new StaleLockOrderException();
            }
            return "done";
        });

        assertThat(appender.list).noneMatch(event -> "Reintentos de transacción agotados"
                .equals(event.getFormattedMessage()));
    }
}
