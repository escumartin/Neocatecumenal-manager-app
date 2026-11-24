package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.DefaultApi;
import io.github.escumartin.neocatecumenal.generated.model.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HealthController implements DefaultApi {

    @Override
    public ResponseEntity<Health> getHealth() {
        return ResponseEntity.ok(new Health("UP"));
    }
}
