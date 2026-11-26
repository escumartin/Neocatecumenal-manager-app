package io.github.escumartin.neocatecumenal.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TransactionRetryTest {

    private static final String EXHAUSTED = "Reintentos de transacción agotados";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final TransactionRetry retry = new TransactionRetry();
    private final AtomicInteger calls = new AtomicInteger();
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
        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new StaleLockOrderException();
        })).isInstanceOf(StaleLockOrderException.class);

        assertThat(calls).hasValue(3);
        assertThat(appender.list).filteredOn(event -> EXHAUSTED.equals(event.getFormattedMessage()))
                .singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                            .containsExactly(Tuple.tuple("calls", 3));
                });
    }

    @Test
    void aSuccessfulRetryLogsNoExhaustion() {
        retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new StaleLockOrderException();
            }
            return "done";
        });

        assertThat(calls).hasValue(2);
        assertThat(appender.list).noneMatch(event -> EXHAUSTED.equals(event.getFormattedMessage()));
    }

    @Test
    void everyStaleAttemptStartsAFreshCall() {
        String result = retry.execute(() -> {
            if (calls.incrementAndGet() <= 2) {
                throw new StaleLockOrderException();
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(calls).hasValue(3);
    }

    @Test
    void otherExceptionsAreNotRetried() {
        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void runRetriesAStaleActionToo() {
        retry.run(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new StaleLockOrderException();
            }
        });

        assertThat(calls).hasValue(2);
    }
}
