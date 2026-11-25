package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingAlreadyStarted;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotActive;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestNotPending;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomHasActiveBookings;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNameTaken;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomConcurrentlyModified;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesForbidden;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest.SpacesProblemHandler;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest.ThrowingSpacesEndpoint;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest.UnauthenticatedException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpacesProblemHandlerTest {

    private final ThrowingSpacesEndpoint endpoint = new ThrowingSpacesEndpoint();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(SpacesProblemHandler.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint, new OutsideEndpoint())
                .setControllerAdvice(new SpacesProblemHandler())
                .build();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(appender);
    }

    static Stream<Arguments> mappings() {
        return Stream.of(
                Arguments.of(new BookingRequestNotPending(), 409, "BOOKING_REQUEST_ALREADY_RESOLVED"),
                Arguments.of(new OptimisticLockingFailureException("stale"), 409, "BOOKING_REQUEST_ALREADY_RESOLVED"),
                Arguments.of(new RoomConcurrentlyModified(), 409, "CONCURRENT_MODIFICATION"),
                Arguments.of(new BookingRequestNotActive(), 409, "BOOKING_REQUEST_NOT_ACTIVE"),
                Arguments.of(new BookingAlreadyStarted(), 409, "BOOKING_ALREADY_STARTED"),
                Arguments.of(new RoomNameTaken(), 409, "ROOM_NAME_TAKEN"),
                Arguments.of(new RoomInactive(), 409, "ROOM_INACTIVE"),
                Arguments.of(new RoomNotFound(), 404, "NOT_FOUND"),
                Arguments.of(new BookingRequestNotFound(), 404, "NOT_FOUND"),
                Arguments.of(new SpacesForbidden(), 403, "FORBIDDEN"),
                Arguments.of(new UnauthenticatedException(), 401, "UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @MethodSource("mappings")
    void mapsEachExceptionToItsStatusAndStableCode(RuntimeException failure, int status, String code)
            throws Exception {
        endpoint.failWith(failure);

        mockMvc.perform(get("/probe"))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    void roomOccupiedCarriesTheConflicts() throws Exception {
        endpoint.failWith(new RoomOccupied(List.of(
                new Conflict(LocalDate.of(2030, 5, 10), new TimeSlot(LocalTime.of(10, 0), LocalTime.of(12, 30))))));

        mockMvc.perform(get("/probe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].date").value("2030-05-10"))
                .andExpect(jsonPath("$.conflicts[0].startTime").value("10:00"))
                .andExpect(jsonPath("$.conflicts[0].endTime").value("12:30"));
    }

    @Test
    void roomOccupiedWithoutKnownConflictsReturnsAnEmptyList() throws Exception {
        endpoint.failWith(new RoomOccupied(List.of()));

        mockMvc.perform(get("/probe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts.length()").value(0));
    }

    @Test
    void roomHasActiveBookingsCarriesTheCount() throws Exception {
        endpoint.failWith(new RoomHasActiveBookings(4));

        mockMvc.perform(get("/probe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.activeBookings").value(4));
    }

    @Test
    void invalidFieldIsAValidationFailureWithItsError() throws Exception {
        endpoint.failWith(new SpacesFieldInvalid("communityId", "COMMUNITY_REQUIRED"));

        mockMvc.perform(get("/probe"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_REQUIRED"));
    }

    @Test
    void aMissingRequestParameterIsARequiredFieldValidationFailure() throws Exception {
        endpoint.failWith(new MissingServletRequestParameterException("date", "LocalDate"));

        mockMvc.perform(get("/probe"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("date"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void logsTheCodeTheStatusTheExceptionAndTheFieldWithoutTheirValues() throws Exception {
        endpoint.failWith(new SpacesFieldInvalid("reason", "TOO_LONG"));

        mockMvc.perform(get("/probe"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                .containsExactlyInAnyOrder(
                        tuple("code", "VALIDATION_FAILED"),
                        tuple("status", 422),
                        tuple("exception", "SpacesFieldInvalid"),
                        tuple("field", "reason"));
        assertThat(event.getFormattedMessage()).doesNotContain("reason", "TOO_LONG");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void logsTheExceptionNameForOtherFailures() throws Exception {
        endpoint.failWith(new RoomNotFound());

        mockMvc.perform(get("/probe"));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                .containsExactlyInAnyOrder(
                        tuple("code", "NOT_FOUND"),
                        tuple("status", 404),
                        tuple("exception", "RoomNotFound"));
    }

    @Test
    void doesNotHandleGenericExceptions() {
        endpoint.failWith(new IllegalStateException("boom"));

        assertThatThrownBy(() -> mockMvc.perform(get("/probe")))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotHandleExceptionsOfControllersOutsideTheModuleRestPackage() {
        assertThatThrownBy(() -> mockMvc.perform(get("/outside")))
                .hasCauseInstanceOf(RoomNotFound.class);
    }

    @Test
    void takesPrecedenceOverTheGlobalHandlers() {
        Order order = SpacesProblemHandler.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
