package io.github.escumartin.neocatecumenal.identity.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class User {

    private final UserId id;
    private final AccessId accessId;
    private DisplayName name;
    private UserRole role;
    private CommunityId communityId;
    private PasswordHash passwordHash;
    private boolean passwordChangeRequired;
    private PrivacyConsent privacyConsent;
    private final long version;
    private final boolean isNew;

    private User(UserId id, AccessId accessId, DisplayName name, UserRole role, CommunityId communityId,
            PasswordHash passwordHash, boolean passwordChangeRequired, PrivacyConsent privacyConsent, long version,
            boolean isNew) {
        this.id = id;
        this.accessId = accessId;
        this.name = name;
        this.role = role;
        this.communityId = communityId;
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = passwordChangeRequired;
        this.privacyConsent = privacyConsent;
        this.version = version;
        this.isNew = isNew;
    }

    public static User register(UserId id, AccessId accessId, DisplayName name, UserRole role,
            CommunityId communityId, PasswordHash initialHash) {
        return new User(id, accessId, name, role, communityId, initialHash, true, null, 0L, true);
    }

    public static User restore(UserId id, AccessId accessId, DisplayName name, UserRole role,
            CommunityId communityId, PasswordHash passwordHash, boolean passwordChangeRequired,
            PrivacyConsent privacyConsent, long version) {
        return new User(id, accessId, name, role, communityId, passwordHash, passwordChangeRequired,
                privacyConsent, version, false);
    }

    public void update(Actor actor, UserChanges changes, MembershipCounts counts) {
        if (!UserManagementPolicy.canEditName(actor, this)) {
            throw new DomainException("FORBIDDEN");
        }
        UserManagementPolicy.checkRoleOrCommunityChange(actor, this, changes.role(), changes.communityId(), counts);
        this.name = changes.name();
        this.role = changes.role();
        this.communityId = changes.communityId();
    }

    public void resetPassword(PasswordHash initialHash) {
        this.passwordHash = initialHash;
        this.passwordChangeRequired = true;
    }

    public void changeOwnPassword(PasswordHash newHash) {
        this.passwordHash = newHash;
        this.passwordChangeRequired = false;
    }

    public void acceptPrivacyNotice(String noticeVersion, Instant now) {
        this.privacyConsent = new PrivacyConsent(noticeVersion, now);
    }

    public void withdrawPrivacyConsent() {
        this.privacyConsent = null;
    }

    public List<PendingStep> pendingSteps(String currentNoticeVersion) {
        List<PendingStep> steps = new ArrayList<>();
        if (privacyConsent == null || !privacyConsent.noticeVersion().equals(currentNoticeVersion)) {
            steps.add(PendingStep.PRIVACY_NOTICE);
        }
        if (passwordChangeRequired) {
            steps.add(PendingStep.PASSWORD_CHANGE);
        }
        return List.copyOf(steps);
    }

    public UserId id() {
        return id;
    }

    public AccessId accessId() {
        return accessId;
    }

    public DisplayName name() {
        return name;
    }

    public UserRole role() {
        return role;
    }

    public CommunityId communityId() {
        return communityId;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    public Optional<PrivacyConsent> privacyConsent() {
        return Optional.ofNullable(privacyConsent);
    }

    public boolean isNew() {
        return isNew;
    }

    public long version() {
        return version;
    }
}
