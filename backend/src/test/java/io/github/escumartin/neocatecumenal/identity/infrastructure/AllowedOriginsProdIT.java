package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Testcontainers
class AllowedOriginsProdIT {

    private static final String ALLOWED_ORIGIN = "https://edge.example.test";
    private static final String OTHER_ORIGIN = "https://evil.example.test";
    private static final String LOGIN_BODY = "{\"accessId\":123456,\"password\":\"wrong-password-value\","
            + "\"tokenDelivery\":\"COOKIE\"}";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ALLOWED_ORIGINS", () -> ALLOWED_ORIGIN);
        registry.add("PRIVACY_NOTICE_VERSION", () -> "v1");
        registry.add("MINIMUM_APP_VERSION", () -> "1.0.0");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Map<String, Filter> filters;

    private static MockHttpServletRequestBuilder login(String origin) {
        return post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY)
                .header(HttpHeaders.ORIGIN, origin);
    }

    @Test
    void stateChangingRequestFromTheConfiguredOriginPassesTheOriginCheck() throws Exception {
        mockMvc.perform(login(ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void stateChangingRequestFromAnotherOriginIsRejected() throws Exception {
        mockMvc.perform(login(OTHER_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void preflightFromTheConfiguredOriginGetsNoCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/sessions")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void noCorsFilterIsRegistered() {
        assertThat(filters.keySet()).noneMatch(name -> name.toLowerCase().contains("cors"));
    }
}
