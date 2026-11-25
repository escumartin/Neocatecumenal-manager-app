package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.in.OwnConsent;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnMemberView;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeOwnContactService;
import io.github.escumartin.neocatecumenal.community.application.service.GetOwnMemberService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ConsentContext;
import io.github.escumartin.neocatecumenal.community.domain.ConsentDecision;
import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.RecorderRole;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class OwnMemberServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-11-20T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GetOwnMemberService get = new GetOwnMemberService(communities, members, users);
    private final ChangeOwnContactService change = new ChangeOwnContactService(communities, members, users, clock);
    private final Community home = communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
            new CommunityNumber(12), Optional.empty(), CommunityStatus.ACTIVE, Optional.empty()));
    private final UserId ownUser = new UserId(UUID.randomUUID());
    private final Actor actor = new Actor(ownUser, ActorRole.REGISTERED, home.id());
    private final UserId moderatorUser = new UserId(UUID.randomUUID());
    private final Actor moderator = new Actor(moderatorUser, ActorRole.MODERATOR, home.id());

    private Member linkedMember() {
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), home.id(), PersonName.of("Ana", "Pérez"),
                Map.of(), List.of(), Optional.of(ownUser)));
    }

    private Member linkedMemberWithVerbalMobile() {
        ConsentRecord verbal = new ConsentRecord(UUID.randomUUID(), ContactField.MOBILE, ConsentDecision.GRANTED,
                ConsentChannel.VERBAL, moderatorUser, RecorderRole.MODERATOR, true, EARLIER);
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), home.id(), PersonName.of("Ana", "Pérez"),
                Map.of(ContactField.MOBILE, "600000001"), List.of(verbal), Optional.of(ownUser)));
    }

    private static ContactChange mobile(boolean shared, String value) {
        return ContactChange.of(Map.of(ContactField.MOBILE,
                shared ? FieldChange.share(value) : FieldChange.withdraw()));
    }

    private OwnConsent currentFor(OwnMemberView view, ContactField field) {
        return view.currentConsents().stream().filter(entry -> entry.record().field() == field).findFirst()
                .orElseThrow();
    }

    @Test
    void gettingOwnMemberWithoutLinkFailsAsNotLinked() {
        assertThatThrownBy(() -> get.get(actor)).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo("MEMBER_NOT_LINKED"));
    }

    @Test
    void changingOwnContactWithoutLinkFailsAsNotLinked() {
        assertThatThrownBy(() -> change.change(actor, mobile(true, "600000001")))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("MEMBER_NOT_LINKED"));
    }

    @Test
    void gettingOwnMemberReturnsTheLinkedMemberAndOnlyThatOne() {
        Member own = linkedMember();
        members.add(Member.register(new MemberId(UUID.randomUUID()), home, PersonName.of("Luis", null),
                Optional.empty(), Optional.empty(), NOW));

        OwnMemberView view = get.get(actor);

        assertThat(view.member().member().id()).isEqualTo(own.id());
        assertThat(view.member().responsible()).isFalse();
        assertThat(view.currentConsents()).isEmpty();
    }

    @Test
    void sharingAFieldRecordsAnAppConsentByTheMemberWithoutRequiringTheNotice() {
        Member own = linkedMember();

        OwnMemberView view = change.change(actor, mobile(true, "600 000 001"));

        assertThat(view.member().member().contact()).containsEntry(ContactField.MOBILE, "600000001");
        OwnConsent consent = currentFor(view, ContactField.MOBILE);
        assertThat(consent.record().decision()).isEqualTo(ConsentDecision.GRANTED);
        assertThat(consent.record().channel()).isEqualTo(ConsentChannel.APP);
        assertThat(consent.record().recorderRole()).isEqualTo(RecorderRole.MEMBER);
        assertThat(consent.record().recordedBy()).isEqualTo(ownUser);
        assertThat(consent.record().recordedAt()).isEqualTo(NOW);
        assertThat(members.stored.get(own.id()).contact()).containsEntry(ContactField.MOBILE, "600000001");
    }

    @Test
    void ownDecisionPrevailsOverAConsentRecordedVerballyByAModerator() {
        linkedMemberWithVerbalMobile();
        users.users.add(new LinkedUser(moderatorUser, ActorRole.MODERATOR, home.id(), "Rosa Gómez"));

        assertThat(currentFor(get.get(actor), ContactField.MOBILE).record().channel())
                .isEqualTo(ConsentChannel.VERBAL);

        OwnMemberView afterWithdrawal = change.change(actor, mobile(false, null));

        OwnConsent consent = currentFor(afterWithdrawal, ContactField.MOBILE);
        assertThat(consent.record().decision()).isEqualTo(ConsentDecision.WITHDRAWN);
        assertThat(consent.record().channel()).isEqualTo(ConsentChannel.APP);
        assertThat(members.stored.values().iterator().next().consents()).hasSize(2);
    }

    @Test
    void withdrawingAFieldErasesItsValue() {
        Member own = linkedMemberWithVerbalMobile();

        OwnMemberView view = change.change(actor, mobile(false, null));

        assertThat(view.member().member().contact()).doesNotContainKey(ContactField.MOBILE);
        assertThat(members.stored.get(own.id()).contact()).isEmpty();
    }

    @Test
    void rectifyingAValueOfAnAlreadySharedFieldKeepsTheConsentRecords() {
        Member own = linkedMemberWithVerbalMobile();

        OwnMemberView view = change.change(actor, mobile(true, "600000002"));

        assertThat(view.member().member().contact()).containsEntry(ContactField.MOBILE, "600000002");
        assertThat(members.stored.get(own.id()).consents()).hasSize(1);
    }

    @Test
    void invalidValueLeavesThePreviousValuesAndConsentsUntouched() {
        Member own = linkedMemberWithVerbalMobile();

        assertThatThrownBy(() -> change.change(actor, mobile(true, "abc")))
                .isInstanceOf(CommunityFieldInvalid.class);

        assertThat(members.stored.get(own.id()).contact()).containsEntry(ContactField.MOBILE, "600000001");
        assertThat(members.stored.get(own.id()).consents()).hasSize(1);
        assertThat(members.log).doesNotContain("member-save");
    }

    @Test
    void changeLocksCommunityThenMemberBeforeSaving() {
        linkedMember();
        members.log = communities.events;

        change.change(actor, mobile(true, "600000001"));

        assertThat(communities.events).containsExactly("lock", "member-lock", "member-save");
    }

    @Test
    void recorderOfAModeratorConsentCarriesRoleAndDisplayName() {
        linkedMemberWithVerbalMobile();
        users.users.add(new LinkedUser(moderatorUser, ActorRole.MODERATOR, home.id(), "Rosa Gómez"));

        OwnConsent consent = currentFor(get.get(actor), ContactField.MOBILE);

        assertThat(consent.recordedBy().role()).isEqualTo(RecorderRole.MODERATOR);
        assertThat(consent.recordedBy().name()).contains("Rosa Gómez");
        assertThat(consent.recordedBy().deleted()).isFalse();
    }

    @Test
    void recorderWhoseUserWasDeletedIsMarkedDeletedWithoutName() {
        linkedMemberWithVerbalMobile();

        OwnConsent consent = currentFor(get.get(actor), ContactField.MOBILE);

        assertThat(consent.recordedBy().role()).isEqualTo(RecorderRole.MODERATOR);
        assertThat(consent.recordedBy().name()).isEmpty();
        assertThat(consent.recordedBy().deleted()).isTrue();
    }

    @Test
    void recorderThatIsTheMemberCarriesNoNameAndIsNotLookedUp() {
        linkedMember();

        OwnMemberView view = change.change(actor, mobile(true, "600000001"));

        OwnConsent consent = currentFor(view, ContactField.MOBILE);
        assertThat(consent.recordedBy().role()).isEqualTo(RecorderRole.MEMBER);
        assertThat(consent.recordedBy().name()).isEmpty();
        assertThat(consent.recordedBy().deleted()).isFalse();
        assertThat(users.looked).isEmpty();
    }

    @Test
    void moderatorCannotChangeConsentOfALinkedMemberStillAfterOwnChanges() {
        Member own = linkedMember();
        change.change(actor, mobile(true, "600000001"));

        assertThatThrownBy(() -> own.changeContact(mobile(false, null),
                Optional.of(ConsentContext.onBehalf(moderator, new OnBehalfConsent(ConsentChannel.VERBAL, true))),
                NOW)).isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("CONSENT_MANAGED_BY_MEMBER"));
    }

    @Test
    void servicesAreTransactional() throws NoSuchMethodException {
        assertThat(ChangeOwnContactService.class.getMethod("change", Actor.class, ContactChange.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(GetOwnMemberService.class.getMethod("get", Actor.class).getAnnotation(Transactional.class)
                .readOnly()).isTrue();
    }
}
