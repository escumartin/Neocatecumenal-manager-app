package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.NeocatecumenalApplication;
import io.github.escumartin.neocatecumenal.identity.CommunityProvisioner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.WebApplicationType;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MissingProvisionerStartupIT {

    private static final String PROVISIONER_BEAN = "communityProvisionerAdapter";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Test
    void startupFailsWhenNoCommunityProvisionerIsRegistered() {
        SpringApplication application = new SpringApplication(NeocatecumenalApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory ->
                ((BeanDefinitionRegistry) beanFactory).removeBeanDefinition(PROVISIONER_BEAN)));

        assertThatThrownBy(() -> application.run("--spring.main.banner-mode=off",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword()))
                .hasMessageContaining(CommunityProvisioner.class.getName());
    }
}
