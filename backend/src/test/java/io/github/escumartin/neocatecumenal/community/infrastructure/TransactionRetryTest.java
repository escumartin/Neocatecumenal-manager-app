package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.community.infrastructure.in.rest.CommunityProblemHandler;
import io.github.escumartin.neocatecumenal.community.infrastructure.in.rest.TransactionRetry;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class TransactionRetryTest {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger staleBeforeSuccess = new AtomicInteger();
    private final AtomicInteger transactionsOpened = new AtomicInteger();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger retryLogger;
    private MockMvc mockMvc;

    @RestController
    class FakeController {

        private final TransactionRetry retry = new TransactionRetry();

        @PostMapping("/test/retry")
        String run() {
            return retry.execute(() -> {
                transactionsOpened.incrementAndGet();
                int call = calls.incrementAndGet();
                if (call <= staleBeforeSuccess.get()) {
                    throw new StaleLockOrderException();
                }
                return "done";
            });
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new CommunityProblemHandler()).build();
        retryLogger = (Logger) LoggerFactory.getLogger(TransactionRetry.class);
        appender.start();
        retryLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        retryLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void exhaustedRetriesLogAWarningWithTheNumberOfCalls() throws Exception {
        staleBeforeSuccess.set(Integer.MAX_VALUE);

        mockMvc.perform(post("/test/retry"));

        assertThat(appender.list).filteredOn(event -> "Reintentos de transacción agotados"
                .equals(event.getFormattedMessage())).singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                            .containsExactly(org.assertj.core.groups.Tuple.tuple("calls", 3));
                });
    }

    @Test
    void aUseCaseThatAlwaysFailsIsCalledThreeTimesAndAnswersConcurrentModification() throws Exception {
        staleBeforeSuccess.set(Integer.MAX_VALUE);

        mockMvc.perform(post("/test/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        assertThat(calls).hasValue(3);
    }

    @Test
    void aSingleStaleLockOrderIsRetriedOnceAndSucceeds() throws Exception {
        staleBeforeSuccess.set(1);

        mockMvc.perform(post("/test/retry")).andExpect(status().is2xxSuccessful());

        assertThat(calls).hasValue(2);
    }

    @Test
    void everyAttemptStartsAFreshCall() throws Exception {
        staleBeforeSuccess.set(2);

        mockMvc.perform(post("/test/retry")).andExpect(status().is2xxSuccessful());

        assertThat(transactionsOpened).hasValue(3);
    }

    @Test
    void otherExceptionsAreNotRetried() {
        TransactionRetry retry = new TransactionRetry();
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> failing = () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> retry.execute(failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(attempts).hasValue(1);
    }
}
