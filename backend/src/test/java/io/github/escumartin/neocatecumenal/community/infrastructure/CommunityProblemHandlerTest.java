package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityForbidden;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.community.infrastructure.in.rest.CommunityProblemHandler;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class CommunityProblemHandlerTest {

    private static final String SECRET = "Ana Test +34600000001 ana.test@example.test";

    @RestController
    static class ThrowingController {

        @GetMapping("/test/domain/{code}")
        String domain(@PathVariable String code) {
            throw new CommunityDomainException(code);
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new CommunityForbidden();
        }

        @GetMapping("/test/community-not-found")
        String communityNotFound() {
            throw new CommunityNotFound();
        }

        @GetMapping("/test/member-not-found")
        String memberNotFound() {
            throw new MemberNotFound();
        }

        @GetMapping("/test/field")
        String field() {
            throw new CommunityFieldInvalid("number", "COMMUNITY_NUMBER_TAKEN");
        }

        @GetMapping("/test/optimistic")
        String optimistic() {
            throw new OptimisticLockingFailureException(SECRET);
        }

        @GetMapping("/test/stale")
        String stale() {
            throw new StaleLockOrderException();
        }

        @GetMapping("/test/identity/{code}")
        String identity(@PathVariable String code) {
            throw new IdentityRuleViolation(code);
        }

        @GetMapping("/test/unexpected")
        String unexpected() {
            throw new IllegalStateException(SECRET);
        }
    }

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger rootLogger;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new CommunityProblemHandler()).build();
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @CsvSource({
        "COMMUNITY_HAS_RESPONSIBLE", "COMMUNITY_NOT_EMPTY", "COMMUNITY_HAS_HISTORY",
        "COMMUNITY_HAS_PRIVILEGED_USERS", "COMMUNITY_DEACTIVATED", "RESPONSIBLE_REQUIRED", "MEMBER_ALREADY_LINKED",
        "USER_ALREADY_LINKED", "USER_LINKED_TO_MEMBER", "MEMBER_NOT_LINKED",
        "CONCURRENT_MODIFICATION"
    })
    void businessConflictsAreAConflictWithTheirStableCode(String code) throws Exception {
        mockMvc.perform(get("/test/domain/" + code))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void consentManagedByTheMemberIsForbiddenWithItsStableCode() throws Exception {
        mockMvc.perform(get("/test/domain/CONSENT_MANAGED_BY_MEMBER"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CONSENT_MANAGED_BY_MEMBER"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void aForbiddenActionIsA403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void aMissingCommunityIsA404() throws Exception {
        mockMvc.perform(get("/test/community-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aMissingMemberIsA404() throws Exception {
        mockMvc.perform(get("/test/member-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anUnauthenticatedCallIsA401() throws Exception {
        mockMvc.perform(get("/test/domain/UNAUTHENTICATED"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aFieldErrorIsAValidationFailureListingTheField() throws Exception {
        mockMvc.perform(get("/test/field"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("number"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_NUMBER_TAKEN"));
    }

    @Test
    void anOptimisticLockFailureIsAConcurrentModification() throws Exception {
        mockMvc.perform(get("/test/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void anExhaustedLockOrderRetryIsAConcurrentModification() throws Exception {
        mockMvc.perform(get("/test/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @ParameterizedTest
    @CsvSource({
        "FORBIDDEN,403,FORBIDDEN",
        "SELF_ACTION_FORBIDDEN,409,SELF_ACTION_FORBIDDEN",
        "LAST_MODERATOR,409,LAST_MODERATOR",
        "LAST_ADMINISTRATOR,409,LAST_ADMINISTRATOR",
        "COMMUNITY_DEACTIVATED,409,COMMUNITY_DEACTIVATED",
        "COMMUNITY_HAS_USERS,409,COMMUNITY_NOT_EMPTY"
    })
    void identityRuleViolationsKeepTheirMeaning(String identityCode, int status, String problemCode)
            throws Exception {
        mockMvc.perform(get("/test/identity/" + identityCode))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(problemCode));
    }

    @Test
    void problemsNeverCarryADetailOrPersonalData() throws Exception {
        String body = mockMvc.perform(get("/test/optimistic")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Ana", "600000001", "example.test").doesNotContain("\"detail\"");
    }

    @Test
    void logsCarryOnlyTheCodeAndTheStatus() throws Exception {
        mockMvc.perform(get("/test/optimistic"));
        mockMvc.perform(get("/test/forbidden"));

        assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("Ana", "600000001", "example.test");
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key).containsExactlyInAnyOrder("code",
                    "status", "exception");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void logsCarryTheSimpleNameOfTheException() throws Exception {
        mockMvc.perform(get("/test/forbidden"));

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getKeyValuePairs())
                .anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("exception");
                    assertThat(pair.value).isEqualTo("CommunityForbidden");
                }));
    }

    @Test
    void anUnknownDomainCodeIsA500LoggedAtErrorWithItsCauseCodeAndStatus() throws Exception {
        mockMvc.perform(get("/test/domain/SOMETHING_NEW"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key, pair -> pair.value)
                    .contains(org.assertj.core.groups.Tuple.tuple("code", "INTERNAL_ERROR"),
                            org.assertj.core.groups.Tuple.tuple("status", 500));
        });
    }

    @Test
    void anUnknownIdentityCodeIsA500LoggedAtErrorWithItsCause() throws Exception {
        mockMvc.perform(get("/test/identity/SOMETHING_NEW"))
                .andExpect(status().isInternalServerError());

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }
}
