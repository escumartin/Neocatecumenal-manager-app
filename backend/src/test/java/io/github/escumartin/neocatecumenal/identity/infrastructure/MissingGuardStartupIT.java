package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.NeocatecumenalApplication;
import io.github.escumartin.neocatecumenal.identity.UserLinkGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.WebApplicationType;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MissingGuardStartupIT {

    private static final String GUARD_BEAN = "userLinkGuardAdapter";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Test
    void startupFailsWhenNoUserLinkGuardIsRegistered() {
        SpringApplication application = new SpringApplication(NeocatecumenalApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory ->
                ((BeanDefinitionRegistry) beanFactory).removeBeanDefinition(GUARD_BEAN)));

        assertThatThrownBy(() -> application.run("--spring.main.banner-mode=off",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword()))
                .hasMessageContaining(UserLinkGuard.class.getName());
    }
}
