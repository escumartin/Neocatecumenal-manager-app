package io.github.escumartin.neocatecumenal.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityApi {

    Optional<CurrentUser> currentUser();

    List<CommunityRef> communities();

    Optional<CommunityRef> community(UUID id);

    boolean lockCommunityForShare(UUID id);

    Optional<UserRef> user(UUID id);

    Optional<UserRef> lockUser(UUID id);

    List<UserRef> usersOf(UUID communityId);

    void moveUser(UUID userId, UUID communityId);

    void deleteUser(UUID userId);
}
