package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.ServiceApi;
import io.github.escumartin.neocatecumenal.generated.model.Compatibility;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.ServiceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServiceInfoController implements ServiceApi {

    private final ServiceProperties properties;

    public ServiceInfoController(ServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public ResponseEntity<Compatibility> getCompatibility() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(new Compatibility(properties.minimumAppVersion(), properties.revision()));
    }
}
