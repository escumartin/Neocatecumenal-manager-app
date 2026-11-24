package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class TomcatProblemErrorsConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> problemErrorReports() {
        return factory -> factory.addContextCustomizers(context -> {
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(ProblemErrorReportValve.class.getName());
            }
        });
    }
}
