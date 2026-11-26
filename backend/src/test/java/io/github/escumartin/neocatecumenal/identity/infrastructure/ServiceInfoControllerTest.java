package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.application.port.in.AuthenticateSession;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ResolvePendingSteps;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.ServiceProperties;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.ProblemDetailsHandler;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest.ServiceInfoController;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.IdentitySecurityConfig;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.OriginCheck;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.SessionCookies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

@WebMvcTest(controllers = ServiceInfoController.class, properties = {
        "service.minimum-app-version=1.4.2", "service.revision=86a5aa6"})
@Import({IdentitySecurityConfig.class, ProblemDetailsHandler.class, SessionCookies.class, OriginCheck.class})
@EnableConfigurationProperties({IdentityProperties.class, ServiceProperties.class})
class ServiceInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticateSession authenticateSession;

    @MockitoBean
    private ResolvePendingSteps resolvePendingSteps;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas() throws IOException {
        Map<String, Object> document = new Yaml().load(Files.readString(Path.of("..", "api", "openapi.yaml")));
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        return (Map<String, Object>) components.get("schemas");
    }

    private static String patternOf(Map<String, Object> schema) {
        return (String) schema.get("pattern");
    }

    @Test
    void compatibilityIsReachableWithoutSessionAndReportsConfiguration() throws Exception {
        mockMvc.perform(get("/api/compatibility"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void compatibilityBodyCarriesOnlyTheTwoDocumentedFields() throws Exception {
        mockMvc.perform(get("/api/compatibility"))
                .andExpect(content().json("{\"minimumAppVersion\":\"1.4.2\",\"serviceRevision\":\"86a5aa6\"}", true));
    }

    @Test
    void compatibilityNeverSetsCookies() throws Exception {
        mockMvc.perform(get("/api/compatibility"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void compatibilityRejectsMethodsOtherThanGet() throws Exception {
        mockMvc.perform(post("/api/compatibility"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void compatibilityIsNeverCached() throws Exception {
        mockMvc.perform(get("/api/compatibility"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void compatibilityResponseMatchesTheContractSchemas() throws Exception {
        String body = mockMvc.perform(get("/api/compatibility")).andReturn().getResponse().getContentAsString();
        Map<String, Object> schemas = schemas();
        Map<String, Object> compatibility = (Map<String, Object>) schemas.get("Compatibility");
        Map<String, Object> properties = (Map<String, Object>) compatibility.get("properties");
        Map<String, Object> version = (Map<String, Object>) schemas.get("AppVersion");
        Map<String, Object> revision = (Map<String, Object>) properties.get("serviceRevision");

        assertThat((String) JsonPath.read(body, "$.minimumAppVersion")).matches(Pattern.compile(patternOf(version)));
        assertThat((String) JsonPath.read(body, "$.serviceRevision")).matches(Pattern.compile(patternOf(revision)));
        assertThat(properties.keySet()).containsExactlyInAnyOrder("minimumAppVersion", "serviceRevision");
    }
}
