package io.github.escumartin.neocatecumenal.community.domain;

import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Member {

    private final MemberId id;
    private CommunityId communityId;
    private PersonName name;
    private final Map<ContactField, String> contact;
    private final List<ConsentRecord> consents;
    private UserId linkedUserId;

    private Member(MemberId id, CommunityId communityId, PersonName name, Map<ContactField, String> contact,
            List<ConsentRecord> consents, UserId linkedUserId) {
        this.id = Objects.requireNonNull(id);
        this.communityId = Objects.requireNonNull(communityId);
        this.name = Objects.requireNonNull(name);
        this.contact = new EnumMap<>(ContactField.class);
        this.contact.putAll(contact);
        this.consents = new ArrayList<>(consents);
        this.linkedUserId = linkedUserId;
    }

    public static Member register(MemberId id, Community community, PersonName name,
            Optional<ContactChange> contact, Optional<ConsentContext> consent, Instant now) {
        community.checkAcceptsNewMembers();
        Member member = new Member(id, community.id(), name, Map.of(), List.of(), null);
        contact.ifPresent(change -> member.changeContact(change, consent, now));
        return member;
    }

    public static Member restore(MemberId id, CommunityId communityId, PersonName name,
            Map<ContactField, String> contact, List<ConsentRecord> consents, Optional<UserId> linkedUserId) {
        return new Member(id, communityId, name, contact, consents, linkedUserId.orElse(null));
    }

    public void rename(PersonName newName) {
        this.name = Objects.requireNonNull(newName);
    }

    public void link(LinkedUser user) {
        if (!user.communityId().equals(communityId)) {
            throw new CommunityFieldInvalid("userId", "USER_NOT_IN_COMMUNITY");
        }
        if (linkedUserId != null) {
            throw new CommunityDomainException("MEMBER_ALREADY_LINKED");
        }
        this.linkedUserId = user.id();
    }

    public void unlink() {
        if (linkedUserId == null) {
            throw new CommunityDomainException("MEMBER_NOT_LINKED");
        }
        this.linkedUserId = null;
    }

    public void transferTo(Community origin, Community target) {
        if (!origin.id().equals(communityId)) {
            throw new IllegalArgumentException("origin is not the member's community");
        }
        if (target.id().equals(communityId)) {
            return;
        }
        target.checkAcceptsNewMembers();
        if (origin.isResponsible(id)) {
            throw new CommunityDomainException("RESPONSIBLE_REQUIRED");
        }
        this.communityId = target.id();
    }

    public void checkDeletable(Community community) {
        if (community.isResponsible(id)) {
            throw new CommunityDomainException("RESPONSIBLE_REQUIRED");
        }
    }

    public void changeContact(ContactChange change, Optional<ConsentContext> consent, Instant now) {
        Map<ContactField, String> normalized = new EnumMap<>(ContactField.class);
        List<ContactField> granted = new ArrayList<>();
        List<ContactField> withdrawn = new ArrayList<>();
        for (ContactField field : ContactField.values()) {
            FieldChange entry = change.entries().get(field);
            if (entry == null) {
                continue;
            }
            boolean sharedNow = contact.containsKey(field);
            if (entry.shared()) {
                normalized.put(field, field.normalize(entry.value()));
                if (!sharedNow) {
                    granted.add(field);
                }
            } else if (sharedNow) {
                withdrawn.add(field);
            }
        }
        boolean consentChanges = !granted.isEmpty() || !withdrawn.isEmpty();
        if (consentChanges) {
            requireConsentContext(consent);
        }
        normalized.forEach(contact::put);
        withdrawn.forEach(contact::remove);
        consent.ifPresent(context -> {
            granted.forEach(field -> record(field, ConsentDecision.GRANTED, context, now));
            withdrawn.forEach(field -> record(field, ConsentDecision.WITHDRAWN, context, now));
        });
    }

    private void requireConsentContext(Optional<ConsentContext> consent) {
        if (linkedUserId != null && !consent.map(this::isTheLinkedMember).orElse(false)) {
            throw new CommunityDomainException("CONSENT_MANAGED_BY_MEMBER");
        }
        ConsentContext context = consent.orElseThrow(
                () -> new CommunityFieldInvalid("onBehalfConsent", "CONSENT_REQUIRED"));
        if (context.needsNotice() && !context.noticeGiven()) {
            throw new CommunityFieldInvalid("onBehalfConsent", "NOTICE_NOT_GIVEN");
        }
    }

    private boolean isTheLinkedMember(ConsentContext context) {
        return context.role() == RecorderRole.MEMBER && context.recordedBy().equals(linkedUserId);
    }

    private void record(ContactField field, ConsentDecision decision, ConsentContext context, Instant now) {
        consents.add(new ConsentRecord(UUID.randomUUID(), field, decision, context.channel(), context.recordedBy(),
                context.role(), context.noticeGiven(), now));
    }

    public MemberId id() {
        return id;
    }

    public CommunityId communityId() {
        return communityId;
    }

    public PersonName name() {
        return name;
    }

    public Map<ContactField, String> contact() {
        return Collections.unmodifiableMap(contact);
    }

    public List<ConsentRecord> consents() {
        return Collections.unmodifiableList(consents);
    }

    public Optional<UserId> linkedUserId() {
        return Optional.ofNullable(linkedUserId);
    }
}
