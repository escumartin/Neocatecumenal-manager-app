package io.github.escumartin.neocatecumenal.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityApi {

    Optional<CurrentUser> currentUser();

    List<CommunityRef> communities();

    Optional<CommunityRef> community(UUID id);
}
