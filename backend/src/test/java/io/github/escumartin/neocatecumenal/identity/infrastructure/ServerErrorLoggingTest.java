package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.ObjectMapper;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.ProblemDetailsHandler;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

class ServerErrorLoggingTest {

    private final ProblemDetailsHandler handler = new ProblemDetailsHandler(new ObjectMapper());
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(ProblemDetailsHandler.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void anErrorResponseWithServerStatusIsLoggedAsAnErrorWithItsCause() {
        AsyncRequestTimeoutException timeout = new AsyncRequestTimeoutException();

        ResponseEntity<Map<String, Object>> response = handler.unexpected(timeout);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "INTERNAL_ERROR").containsEntry("status", 503);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(AsyncRequestTimeoutException.class.getName());
        });
    }

    @Test
    void anErrorResponseWithServerStatusKeepsItsHeaders() {
        ErrorResponseException unavailable = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.RETRY_AFTER, "30");
                return headers;
            }
        };

        ResponseEntity<Map<String, Object>> response = handler.unexpected(unavailable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    }

    @Test
    void anErrorResponseWithClientStatusIsNotLoggedAsAnError() {
        ResponseEntity<Map<String, Object>> response = handler.unexpected(
                new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }
}
