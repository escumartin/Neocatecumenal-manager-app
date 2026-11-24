package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.Role;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListCommunities;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class IdentityApiService implements IdentityApi {

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final CommunityRepository communities;
    private final PrivacyNoticeSettings privacyNotice;
    private final ListCommunities listCommunities;

    public IdentityApiService(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice, ListCommunities listCommunities) {
        this.currentSession = currentSession;
        this.users = users;
        this.communities = communities;
        this.privacyNotice = privacyNotice;
        this.listCommunities = listCommunities;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CurrentUser> currentUser() {
        return currentSession.current()
                .flatMap(session -> users.findById(session.userId()))
                .filter(user -> user.pendingSteps(privacyNotice.currentVersion()).isEmpty())
                .map(IdentityApiService::toCurrentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityRef> communities() {
        return listCommunities.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommunityRef> community(UUID id) {
        return communities.findById(new CommunityId(id))
                .map(community -> new CommunityRef(community.id().value(), community.number().value()));
    }

    private static CurrentUser toCurrentUser(User user) {
        return new CurrentUser(user.id().value(), user.accessId().value(), user.name().value(),
                Role.valueOf(user.role().name()), user.communityId().value());
    }
}
