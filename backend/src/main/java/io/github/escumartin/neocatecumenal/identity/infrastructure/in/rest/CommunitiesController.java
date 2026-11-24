package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.CommunitiesApi;
import io.github.escumartin.neocatecumenal.generated.model.Community;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListCommunities;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommunitiesController implements CommunitiesApi {

    private final IdentityApi identityApi;
    private final ListCommunities listCommunities;

    CommunitiesController(IdentityApi identityApi, ListCommunities listCommunities) {
        this.identityApi = identityApi;
        this.listCommunities = listCommunities;
    }

    @Override
    public ResponseEntity<List<Community>> listCommunities() {
        if (identityApi.currentUser().isEmpty()) {
            throw new AccessDeniedException("Pasos de primer acceso pendientes");
        }
        List<Community> communities = listCommunities.list().stream()
                .map(community -> new Community().id(community.id()).number(community.number()))
                .toList();
        return ResponseEntity.ok(communities);
    }
}
