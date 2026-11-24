package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.MyProfile;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.domain.User;

class MyProfileAssembler {

    private final CommunityRepository communities;
    private final PrivacyNoticeSettings privacyNotice;

    MyProfileAssembler(CommunityRepository communities, PrivacyNoticeSettings privacyNotice) {
        this.communities = communities;
        this.privacyNotice = privacyNotice;
    }

    MyProfile assemble(User user) {
        String currentVersion = privacyNotice.currentVersion();
        var community = communities.findById(user.communityId()).orElseThrow(
                () -> new IllegalStateException("Comunidad inexistente para un usuario existente"));
        return new MyProfile(user.id(), user.accessId(), user.name(), user.role(), user.communityId(),
                community.number(), user.pendingSteps(currentVersion), currentVersion, user.privacyConsent());
    }
}
