package io.github.escumartin.neocatecumenal.identity;

import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;

public final class TestUsers {

    private TestUsers() {
    }

    public static User newUser(UserId id, AccessId accessId, DisplayName name, UserRole role,
            CommunityId communityId, PasswordHash hash, boolean passwordChangeRequired, PrivacyConsent consent) {
        User user = User.register(id, accessId, name, role, communityId, hash);
        if (!passwordChangeRequired) {
            user.changeOwnPassword(hash);
        }
        if (consent != null) {
            user.acceptPrivacyNotice(consent.noticeVersion(), consent.acceptedAt());
        }
        return user;
    }
}
