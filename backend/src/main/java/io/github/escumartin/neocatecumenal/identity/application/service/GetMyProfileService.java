package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.GetMyProfile;
import io.github.escumartin.neocatecumenal.identity.application.port.in.MyProfile;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class GetMyProfileService implements GetMyProfile {

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final MyProfileAssembler profiles;

    public GetMyProfileService(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice) {
        this.currentSession = currentSession;
        this.users = users;
        this.profiles = new MyProfileAssembler(communities, privacyNotice);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MyProfile> get() {
        return currentSession.current()
                .flatMap(session -> users.findById(session.userId()))
                .map(profiles::assemble);
    }
}
