package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.application.port.in.AuthenticateSession;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ResolvePendingSteps;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.ProblemDetailsHandler;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.IdentitySecurityConfig;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.OriginCheck;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.SessionCookies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityBaselineTest.ProbeController.class)
@Import({IdentitySecurityConfig.class, ProblemDetailsHandler.class, SessionCookies.class, OriginCheck.class,
        SecurityBaselineTest.ProbeController.class})
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityBaselineTest {

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/api/probe/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/api/health")
        String health() {
            return "up";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticateSession authenticateSession;

    @MockitoBean
    private ResolvePendingSteps resolvePendingSteps;

    @Test
    void visitorOnProtectedRouteGetsUnauthenticatedProblem() throws Exception {
        mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    void authenticatedUserReachesProtectedRoute() throws Exception {
        mockMvc.perform(get("/api/probe").with(user("tester")))
                .andExpect(status().isOk());
    }

    @Test
    void deniedAccessGetsForbiddenProblem() throws Exception {
        mockMvc.perform(get("/api/probe/denied").with(user("tester")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void loginRouteIsNotBlockedByAuthentication() throws Exception {
        mockMvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void otherRoutesThatDoNotExistRequireAuthenticationFirst() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownRouteForAuthenticatedUserGetsNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/unknown").with(user("tester")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void stateChangingRequestWithoutSessionIsRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        mockMvc.perform(get("/api/probe").with(user("tester")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
