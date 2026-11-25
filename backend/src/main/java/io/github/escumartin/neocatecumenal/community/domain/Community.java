package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;
import java.util.Optional;

public final class Community {

    private final CommunityId id;
    private CommunityNumber number;
    private CommunityName name;
    private CommunityStatus status;
    private final MemberId responsibleMemberId;

    private Community(CommunityId id, CommunityNumber number, CommunityName name, CommunityStatus status,
            MemberId responsibleMemberId) {
        this.id = Objects.requireNonNull(id);
        this.number = Objects.requireNonNull(number);
        this.name = name;
        this.status = Objects.requireNonNull(status);
        this.responsibleMemberId = responsibleMemberId;
    }

    public static Community create(CommunityId id, CommunityNumber number, Optional<CommunityName> name) {
        return new Community(id, number, name.orElse(null), CommunityStatus.ACTIVE, null);
    }

    public static Community restore(CommunityId id, CommunityNumber number, Optional<CommunityName> name,
            CommunityStatus status, Optional<MemberId> responsibleMemberId) {
        return new Community(id, number, name.orElse(null), status, responsibleMemberId.orElse(null));
    }

    public void renumber(CommunityNumber newNumber) {
        this.number = Objects.requireNonNull(newNumber);
    }

    public void rename(Optional<CommunityName> newName) {
        this.name = newName.orElse(null);
    }

    public void deactivate(boolean privilegedUsersPresent, boolean confirmed) {
        if (privilegedUsersPresent && !confirmed) {
            throw new CommunityDomainException("COMMUNITY_HAS_PRIVILEGED_USERS");
        }
        this.status = CommunityStatus.DEACTIVATED;
    }

    public void reactivate() {
        this.status = CommunityStatus.ACTIVE;
    }

    public void checkAcceptsNewMembers() {
        if (status == CommunityStatus.DEACTIVATED) {
            throw new CommunityDomainException("COMMUNITY_DEACTIVATED");
        }
    }

    public void checkDeletable(long members, long users, boolean hasHistory) {
        if (hasResponsible()) {
            throw new CommunityDomainException("COMMUNITY_HAS_RESPONSIBLE");
        }
        if (members > 0 || users > 0) {
            throw new CommunityDomainException("COMMUNITY_NOT_EMPTY");
        }
        if (hasHistory) {
            throw new CommunityDomainException("COMMUNITY_HAS_HISTORY");
        }
    }

    public boolean hasResponsible() {
        return responsibleMemberId != null;
    }

    public CommunityId id() {
        return id;
    }

    public CommunityNumber number() {
        return number;
    }

    public Optional<CommunityName> name() {
        return Optional.ofNullable(name);
    }

    public CommunityStatus status() {
        return status;
    }

    public Optional<MemberId> responsibleMemberId() {
        return Optional.ofNullable(responsibleMemberId);
    }
}
