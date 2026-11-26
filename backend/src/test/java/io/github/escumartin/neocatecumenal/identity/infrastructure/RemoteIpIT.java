package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.Filter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RemoteIpIT {

    private static final String EDGE_ADDRESS_PATTERN = "172\\.28\\.250\\.10";
    private static final String FORWARDED_CLIENT = "203.0.113.9";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    record Observed(String remoteAddress, String scheme) {
    }

    @TestConfiguration
    static class ObservingConfiguration {

        static final AtomicReference<Observed> LAST = new AtomicReference<>();

        @Bean
        Filter observingFilter() {
            return (request, response, chain) -> {
                LAST.set(new Observed(request.getRemoteAddr(), request.getScheme()));
                chain.doFilter(request, response);
            };
        }
    }

    private static void productionProperties(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ALLOWED_ORIGINS", () -> "https://edge.example.test");
        registry.add("PRIVACY_NOTICE_VERSION", () -> "v1");
        registry.add("MINIMUM_APP_VERSION", () -> "1.0.0");
    }

    private static Observed callHealth(int port, String forwardedFor) throws Exception {
        ObservingConfiguration.LAST.set(null);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/health"))
                .header("X-Forwarded-For", forwardedFor)
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return ObservingConfiguration.LAST.get();
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("prod")
    @Import(ObservingConfiguration.class)
    class FromAnUntrustedPeer {

        @LocalServerPort
        int port;

        @Autowired
        Environment environment;

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            productionProperties(registry);
        }

        @Test
        void onlyTheFixedAddressOfEdgeIsTrustedAsProxy() {
            assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
            assertThat(environment.getProperty("server.tomcat.remoteip.internal-proxies"))
                    .isEqualTo(EDGE_ADDRESS_PATTERN);
        }

        @Test
        void forwardingHeadersAreIgnoredAndTheRemoteAddressIsKept() throws Exception {
            Observed observed = callHealth(port, FORWARDED_CLIENT);

            assertThat(observed.remoteAddress()).isEqualTo("127.0.0.1");
            assertThat(observed.scheme()).isEqualTo("http");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1")
    @ActiveProfiles("prod")
    @Import(ObservingConfiguration.class)
    class FromTheTrustedPeer {

        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            productionProperties(registry);
        }

        @BeforeEach
        void clear() {
            ObservingConfiguration.LAST.set(null);
        }

        @Test
        void forwardedForAndForwardedProtoAreRespected() throws Exception {
            Observed observed = callHealth(port, FORWARDED_CLIENT);

            assertThat(observed.remoteAddress()).isEqualTo(FORWARDED_CLIENT);
            assertThat(observed.scheme()).isEqualTo("https");
        }
    }
}
