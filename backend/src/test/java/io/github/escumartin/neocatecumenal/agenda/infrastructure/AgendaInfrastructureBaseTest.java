package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.agenda.domain.Actor;
import io.github.escumartin.neocatecumenal.agenda.domain.ActorRole;
import io.github.escumartin.neocatecumenal.agenda.domain.AgendaFieldInvalid;
import io.github.escumartin.neocatecumenal.agenda.domain.AgendaForbidden;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.agenda.domain.Conflict;
import io.github.escumartin.neocatecumenal.agenda.domain.EventNotFound;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomNotFound;
import io.github.escumartin.neocatecumenal.agenda.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.agenda.domain.UserId;
import io.github.escumartin.neocatecumenal.agenda.infrastructure.in.rest.ActorResolver;
import io.github.escumartin.neocatecumenal.agenda.infrastructure.in.rest.AgendaProblemHandler;
import io.github.escumartin.neocatecumenal.agenda.infrastructure.in.rest.ThrowingAgendaEndpoint;
import io.github.escumartin.neocatecumenal.agenda.infrastructure.in.rest.UnauthenticatedException;
import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.Role;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgendaInfrastructureBaseTest {

    @Nested
    @SpringJUnitConfig(ActorResolver.class)
    class ActorResolution {

        private static final UUID USER = UUID.randomUUID();
        private static final UUID COMMUNITY = UUID.randomUUID();

        @MockitoBean
        private IdentityApi identity;

        @Autowired
        private ActorResolver resolver;

        private void signedInAs(Role role) {
            when(identity.currentUser()).thenReturn(Optional.of(new CurrentUser(USER, 123456, "Test Person", role,
                    COMMUNITY)));
        }

        @Test
        void copiesOnlyUserRoleAndCommunity() {
            signedInAs(Role.MODERATOR);

            Actor actor = resolver.currentActor();

            assertThat(actor).isEqualTo(new Actor(new UserId(USER), ActorRole.MODERATOR,
                    new CommunityId(COMMUNITY)));
            assertThat(Arrays.stream(Actor.class.getRecordComponents()).map(component -> component.getName()))
                    .containsExactly("userId", "role", "ownCommunityId");
        }

        @Test
        void mapsEveryIdentityRole() {
            signedInAs(Role.REGISTERED);
            assertThat(resolver.currentActor().role()).isEqualTo(ActorRole.REGISTERED);

            signedInAs(Role.ADMINISTRATOR);
            assertThat(resolver.currentActor().role()).isEqualTo(ActorRole.ADMINISTRATOR);
        }

        @Test
        void failsAsUnauthenticatedWithoutACurrentUser() {
            when(identity.currentUser()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> resolver.currentActor()).isInstanceOf(UnauthenticatedException.class);
        }

        @Test
        void administratorCanChooseAnExistingCommunity() {
            signedInAs(Role.ADMINISTRATOR);
            UUID chosen = UUID.randomUUID();
            when(identity.community(chosen)).thenReturn(Optional.of(new CommunityRef(chosen, 7, true)));

            Optional<CommunityId> requested = resolver.requestedCommunity(resolver.currentActor(), chosen);

            assertThat(requested).contains(new CommunityId(chosen));
        }

        @Test
        void administratorChoosingAnUnknownCommunityFailsValidation() {
            signedInAs(Role.ADMINISTRATOR);
            UUID unknown = UUID.randomUUID();
            when(identity.community(unknown)).thenReturn(Optional.empty());
            Actor actor = resolver.currentActor();

            assertThatThrownBy(() -> resolver.requestedCommunity(actor, unknown))
                    .isInstanceOfSatisfying(AgendaFieldInvalid.class, exception -> {
                        assertThat(exception.field()).isEqualTo("communityId");
                        assertThat(exception.code()).isEqualTo("COMMUNITY_NOT_FOUND");
                    });
        }

        @Test
        void administratorWithoutChoiceGetsNoRequestedCommunity() {
            signedInAs(Role.ADMINISTRATOR);

            assertThat(resolver.requestedCommunity(resolver.currentActor(), null)).isEmpty();
        }

        @Test
        void moderatorRequestedCommunityIsPassedThroughWithoutLookup() {
            signedInAs(Role.MODERATOR);
            UUID other = UUID.randomUUID();

            Optional<CommunityId> requested = resolver.requestedCommunity(resolver.currentActor(), other);

            assertThat(requested).contains(new CommunityId(other));
            verify(identity, never()).community(any());
        }
    }

    @Nested
    class ProblemHandling {

        private final ThrowingAgendaEndpoint endpoint = new ThrowingAgendaEndpoint();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(AgendaProblemHandler.class);
        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.standaloneSetup(endpoint, new OutsideEndpoint())
                    .setControllerAdvice(new AgendaProblemHandler())
                    .build();
            appender.start();
            handlerLogger.addAppender(appender);
        }

        @AfterEach
        void tearDown() {
            handlerLogger.detachAppender(appender);
        }

        @Test
        void logsTheCodeTheStatusTheExceptionAndTheFieldWithoutTheirValues() throws Exception {
            endpoint.failWith(new AgendaFieldInvalid("title", "TOO_LONG"));

            mockMvc.perform(get("/probe"));

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactlyInAnyOrder(
                            tuple("code", "VALIDATION_FAILED"),
                            tuple("status", 422),
                            tuple("exception", "AgendaFieldInvalid"),
                            tuple("field", "title"));
        }

        @Test
        void logsTheExceptionNameForOtherFailures() throws Exception {
            endpoint.failWith(new EventNotFound());

            mockMvc.perform(get("/probe"));

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactlyInAnyOrder(
                            tuple("code", "NOT_FOUND"),
                            tuple("status", 404),
                            tuple("exception", "EventNotFound"));
        }

        static Stream<Arguments> mappings() {
            return Stream.of(
                    Arguments.of(new RoomInactive(), 409, "ROOM_INACTIVE"),
                    Arguments.of(new RoomNotFound(), 404, "NOT_FOUND"),
                    Arguments.of(new EventNotFound(), 404, "NOT_FOUND"),
                    Arguments.of(new AgendaForbidden(), 403, "FORBIDDEN"),
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
            endpoint.failWith(new RoomOccupied(List.of(new Conflict(LocalDate.of(2030, 5, 10),
                    new TimeSlot(LocalTime.of(10, 0), LocalTime.of(12, 30))))));

            mockMvc.perform(get("/probe"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"))
                    .andExpect(jsonPath("$.conflicts.length()").value(1))
                    .andExpect(jsonPath("$.conflicts[0].date").value("2030-05-10"))
                    .andExpect(jsonPath("$.conflicts[0].startTime").value("10:00"))
                    .andExpect(jsonPath("$.conflicts[0].endTime").value("12:30"));
        }

        @Test
        void invalidFieldIsAValidationFailureWithItsError() throws Exception {
            endpoint.failWith(new AgendaFieldInvalid("communityId", "COMMUNITY_REQUIRED"));

            mockMvc.perform(get("/probe"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                    .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_REQUIRED"));
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
                    .hasCauseInstanceOf(EventNotFound.class);
        }

        @Test
        void takesPrecedenceOverTheGlobalHandlers() {
            Order order = AgendaProblemHandler.class.getAnnotation(Order.class);

            assertThat(order).isNotNull();
            assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }
    }
}
