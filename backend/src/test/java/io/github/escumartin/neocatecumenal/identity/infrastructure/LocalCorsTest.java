package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.security.LocalCorsConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocalCorsTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(LocalCorsConfig.class)
            .withBean(IdentityProperties.class,
                    () -> new IdentityProperties("v1", List.of("http://localhost:8081"),
                            new IdentityProperties.Bootstrap(false, List.of(), null, null)));

    @Test
    void defaultProfileHasNoCorsFilter() {
        runner.run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void localProfileAnswersPreflightFromAllowedOriginWithCredentials() {
        runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<jakarta.servlet.Filter> registration = context.getBean(FilterRegistrationBean.class);
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/sessions");
            request.addHeader("Origin", "http://localhost:8081");
            request.addHeader("Access-Control-Request-Method", "POST");
            MockHttpServletResponse response = new MockHttpServletResponse();
            registration.getFilter().doFilter(request, response, new MockFilterChain());
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:8081");
            assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        });
    }

    @Test
    void localProfileRejectsOtherOrigins() {
        runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<jakarta.servlet.Filter> registration = context.getBean(FilterRegistrationBean.class);
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/sessions");
            request.addHeader("Origin", "http://evil.example");
            request.addHeader("Access-Control-Request-Method", "POST");
            MockHttpServletResponse response = new MockHttpServletResponse();
            registration.getFilter().doFilter(request, response, new MockFilterChain());
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        });
    }
}
