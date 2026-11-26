package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.application.port.in.AuthenticateSession;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ResolvePendingSteps;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.HealthController;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.ProblemDetailsHandler;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.IdentitySecurityConfig;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.OriginCheck;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.SessionCookies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import({IdentitySecurityConfig.class, ProblemDetailsHandler.class, SessionCookies.class, OriginCheck.class})
@EnableConfigurationProperties(IdentityProperties.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticateSession authenticateSession;

    @MockitoBean
    private ResolvePendingSteps resolvePendingSteps;

    @Test
    void healthIsReachableWithoutSessionAndReportsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthBodyCarriesOnlyTheStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }
}
