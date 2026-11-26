package io.github.escumartin.neocatecumenal;

import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
class ProductionConfigurationGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final String ALLOWED_ORIGINS = "identity.allowed-origins";
    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.datasource.url",
            "identity.privacy-notice-version",
            "service.minimum-app-version");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        verify();
    }

    void verify() {
        if (environment.matchesProfiles("local")) {
            throw new IllegalStateException("El perfil prod no puede activarse junto al perfil local");
        }
        REQUIRED_PROPERTIES.forEach(this::requireResolved);
        requireAllowedOrigins();
    }

    private void requireResolved(String property) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException unresolved) {
            throw missing(property);
        }
        if (value == null || value.isBlank()) {
            throw missing(property);
        }
    }

    private void requireAllowedOrigins() {
        List<String> origins;
        try {
            origins = Binder.get(environment).bind(ALLOWED_ORIGINS, Bindable.listOf(String.class))
                    .orElse(List.of());
        } catch (RuntimeException unresolved) {
            throw missing(ALLOWED_ORIGINS);
        }
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin == null || origin.isBlank() || origin.contains("${"))) {
            throw missing(ALLOWED_ORIGINS);
        }
    }

    private static IllegalStateException missing(String property) {
        return new IllegalStateException("Falta la propiedad obligatoria " + property + " en el perfil prod");
    }
}
