package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MemberTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final UserId MODERATOR = new UserId(UUID.randomUUID());
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());
    private static final PersonName NAME = PersonName.of("Ana", "Pérez");

    private static Community community(CommunityStatus status) {
        return Community.restore(COMMUNITY, new CommunityNumber(4), Optional.empty(), status, Optional.empty());
    }

    private static ConsentContext verbal() {
        return new ConsentContext(MODERATOR, RecorderRole.MODERATOR, ConsentChannel.VERBAL, true);
    }

    private static ContactChange change(ContactField field, FieldChange change) {
        return ContactChange.of(Map.of(field, change));
    }

    private static Member member() {
        return Member.register(new MemberId(UUID.randomUUID()), community(CommunityStatus.ACTIVE), NAME,
                Optional.empty(), Optional.empty(), NOW);
    }

    private static Member memberSharing(ContactField field, String value) {
        Member member = member();
        member.changeContact(change(field, FieldChange.share(value)), Optional.of(verbal()), NOW);
        return member;
    }

    private static Member linkedMember(UserId user, Map<ContactField, String> contact) {
        return Member.restore(new MemberId(UUID.randomUUID()), COMMUNITY, NAME, contact, List.of(),
                Optional.of(user));
    }

    private static void assertInvalid(Runnable action, String field, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
            assertThat(e.field()).isEqualTo(field);
            assertThat(e.code()).isEqualTo(code);
        });
    }

    @Test
    void registerInADeactivatedCommunityFails() {
        assertThatThrownBy(() -> Member.register(new MemberId(UUID.randomUUID()),
                community(CommunityStatus.DEACTIVATED), NAME, Optional.empty(), Optional.empty(), NOW))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED"));
    }

    @Test
    void registerWithoutContactKeepsOnlyTheNameInTheCommunity() {
        Member member = member();

        assertThat(member.communityId()).isEqualTo(COMMUNITY);
        assertThat(member.name()).isEqualTo(NAME);
        assertThat(member.contact()).isEmpty();
        assertThat(member.consents()).isEmpty();
        assertThat(member.linkedUserId()).isEmpty();
    }

    @Test
    void registerWithSharedContactStoresNormalizedValuesAndOneGrantPerField() {
        ContactChange contact = ContactChange.of(Map.of(
                ContactField.MOBILE, FieldChange.share("600 000 001"),
                ContactField.EMAIL, FieldChange.share("ana@example.org"),
                ContactField.LANDLINE, FieldChange.withdraw()));

        Member member = Member.register(new MemberId(UUID.randomUUID()), community(CommunityStatus.ACTIVE), NAME,
                Optional.of(contact), Optional.of(verbal()), NOW);

        assertThat(member.contact()).containsOnly(Map.entry(ContactField.MOBILE, "600000001"),
                Map.entry(ContactField.EMAIL, "ana@example.org"));
        assertThat(member.consents()).extracting(ConsentRecord::field, ConsentRecord::decision)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ContactField.MOBILE, ConsentDecision.GRANTED),
                        org.assertj.core.groups.Tuple.tuple(ContactField.EMAIL, ConsentDecision.GRANTED));
        assertThat(member.consents()).allSatisfy(record -> {
            assertThat(record.recordedBy()).isEqualTo(MODERATOR);
            assertThat(record.recorderRole()).isEqualTo(RecorderRole.MODERATOR);
            assertThat(record.channel()).isEqualTo(ConsentChannel.VERBAL);
            assertThat(record.noticeGiven()).isTrue();
            assertThat(record.recordedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void registerWithContactButNothingSharedNeedsNoConsentContext() {
        ContactChange contact = ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.withdraw()));

        Member member = Member.register(new MemberId(UUID.randomUUID()), community(CommunityStatus.ACTIVE), NAME,
                Optional.of(contact), Optional.empty(), NOW);

        assertThat(member.contact()).isEmpty();
        assertThat(member.consents()).isEmpty();
    }

    @Test
    void registerWithSharedContactAndNoConsentContextFails() {
        ContactChange contact = change(ContactField.MOBILE, FieldChange.share("600000001"));

        assertInvalid(() -> Member.register(new MemberId(UUID.randomUUID()), community(CommunityStatus.ACTIVE), NAME,
                Optional.of(contact), Optional.empty(), NOW), "onBehalfConsent", "CONSENT_REQUIRED");
    }

    @ParameterizedTest
    @EnumSource(value = ConsentChannel.class, names = {"VERBAL", "WRITTEN"})
    void registerWithoutNoticeGivenFailsAndStoresNothing(ConsentChannel channel) {
        ConsentContext withoutNotice = new ConsentContext(MODERATOR, RecorderRole.MODERATOR, channel, false);
        ContactChange contact = change(ContactField.MOBILE, FieldChange.share("600000001"));

        assertInvalid(() -> Member.register(new MemberId(UUID.randomUUID()), community(CommunityStatus.ACTIVE), NAME,
                Optional.of(contact), Optional.of(withoutNotice), NOW), "onBehalfConsent", "NOTICE_NOT_GIVEN");
    }

    @Test
    void sharingAFieldGrantsConsentAndStoresTheValue() {
        Member member = member();

        member.changeContact(change(ContactField.EMAIL, FieldChange.share("ana@example.org")),
                Optional.of(verbal()), NOW);

        assertThat(member.contact()).containsOnly(Map.entry(ContactField.EMAIL, "ana@example.org"));
        assertThat(member.consents()).hasSize(1);
        assertThat(member.consents().get(0).decision()).isEqualTo(ConsentDecision.GRANTED);
        assertThat(member.consents().get(0).field()).isEqualTo(ContactField.EMAIL);
    }

    @Test
    void sharingWithoutAValueFailsWithRequiredAndChangesNothing() {
        Member member = member();

        assertInvalid(() -> member.changeContact(change(ContactField.MOBILE, FieldChange.share(null)),
                Optional.of(verbal()), NOW), "mobile", "REQUIRED");
        assertThat(member.contact()).isEmpty();
        assertThat(member.consents()).isEmpty();
    }

    @Test
    void anInvalidPhoneFailsOnItsFieldAndChangesNothingElse() {
        Member member = member();
        ContactChange both = ContactChange.of(Map.of(
                ContactField.EMAIL, FieldChange.share("ana@example.org"),
                ContactField.LANDLINE, FieldChange.share("512345678")));

        assertInvalid(() -> member.changeContact(both, Optional.of(verbal()), NOW), "landline", "PHONE_INVALID");
        assertThat(member.contact()).isEmpty();
        assertThat(member.consents()).isEmpty();
    }

    @Test
    void anInvalidEmailFailsOnItsField() {
        Member member = member();

        assertInvalid(() -> member.changeContact(change(ContactField.EMAIL, FieldChange.share("a@b")),
                Optional.of(verbal()), NOW), "email", "EMAIL_INVALID");
    }

    @Test
    void withdrawingErasesTheValueAndRecordsAWithdrawalWithoutIt() {
        Member member = memberSharing(ContactField.MOBILE, "600000001");

        member.changeContact(change(ContactField.MOBILE, FieldChange.withdraw()), Optional.of(verbal()), NOW);

        assertThat(member.contact()).isEmpty();
        assertThat(member.consents()).extracting(ConsentRecord::decision)
                .containsExactly(ConsentDecision.GRANTED, ConsentDecision.WITHDRAWN);
    }

    @Test
    void changingAValueWhileStillSharedIsARectificationWithoutANewRecord() {
        Member member = memberSharing(ContactField.MOBILE, "600000001");

        member.changeContact(change(ContactField.MOBILE, FieldChange.share("600000002")), Optional.empty(), NOW);

        assertThat(member.contact()).containsOnly(Map.entry(ContactField.MOBILE, "600000002"));
        assertThat(member.consents()).hasSize(1);
    }

    @Test
    void withdrawingAFieldThatIsNotSharedChangesNothing() {
        Member member = member();

        member.changeContact(change(ContactField.MOBILE, FieldChange.withdraw()), Optional.empty(), NOW);

        assertThat(member.contact()).isEmpty();
        assertThat(member.consents()).isEmpty();
    }

    @Test
    void fieldsNotMentionedAreLeftUntouched() {
        Member member = memberSharing(ContactField.MOBILE, "600000001");

        member.changeContact(change(ContactField.EMAIL, FieldChange.share("ana@example.org")),
                Optional.of(verbal()), NOW);

        assertThat(member.contact()).containsEntry(ContactField.MOBILE, "600000001")
                .containsEntry(ContactField.EMAIL, "ana@example.org");
    }

    @Test
    void aConsentChangeWithoutContextFails() {
        Member member = member();

        assertInvalid(() -> member.changeContact(change(ContactField.MOBILE, FieldChange.share("600000001")),
                Optional.empty(), NOW), "onBehalfConsent", "CONSENT_REQUIRED");
        assertThat(member.contact()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ConsentChannel.class, names = {"VERBAL", "WRITTEN"})
    void aConsentChangeWithoutNoticeFails(ConsentChannel channel) {
        Member member = member();
        ConsentContext withoutNotice = new ConsentContext(MODERATOR, RecorderRole.MODERATOR, channel, false);

        assertInvalid(() -> member.changeContact(change(ContactField.MOBILE, FieldChange.share("600000001")),
                Optional.of(withoutNotice), NOW), "onBehalfConsent", "NOTICE_NOT_GIVEN");
        assertThat(member.contact()).isEmpty();
        assertThat(member.consents()).isEmpty();
    }

    @Test
    void aWithdrawalOnBehalfAlsoNeedsTheNotice() {
        Member member = memberSharing(ContactField.MOBILE, "600000001");
        ConsentContext withoutNotice = new ConsentContext(MODERATOR, RecorderRole.MODERATOR,
                ConsentChannel.WRITTEN, false);

        assertInvalid(() -> member.changeContact(change(ContactField.MOBILE, FieldChange.withdraw()),
                Optional.of(withoutNotice), NOW), "onBehalfConsent", "NOTICE_NOT_GIVEN");
        assertThat(member.contact()).containsKey(ContactField.MOBILE);
    }

    @Test
    void aLinkedMemberCannotHaveItsSharedFieldsChangedByAModerator() {
        Member member = linkedMember(new UserId(UUID.randomUUID()), Map.of());

        assertThatThrownBy(() -> member.changeContact(change(ContactField.MOBILE, FieldChange.share("600000001")),
                Optional.of(verbal()), NOW)).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo("CONSENT_MANAGED_BY_MEMBER"));
    }

    @Test
    void aLinkedMemberCannotHaveAFieldWithdrawnByAModerator() {
        Member member = linkedMember(new UserId(UUID.randomUUID()), Map.of(ContactField.MOBILE, "600000001"));

        assertThatThrownBy(() -> member.changeContact(change(ContactField.MOBILE, FieldChange.withdraw()),
                Optional.of(verbal()), NOW)).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo("CONSENT_MANAGED_BY_MEMBER"));
    }

    @Test
    void aModeratorMayRectifyTheValueOfAFieldTheLinkedMemberAlreadyShares() {
        Member member = linkedMember(new UserId(UUID.randomUUID()), Map.of(ContactField.MOBILE, "600000001"));

        member.changeContact(change(ContactField.MOBILE, FieldChange.share("600000002")), Optional.empty(), NOW);

        assertThat(member.contact()).containsEntry(ContactField.MOBILE, "600000002");
        assertThat(member.consents()).isEmpty();
    }

    @Test
    void aLinkedMemberDecidesForThemselvesThroughTheApp() {
        UserId self = new UserId(UUID.randomUUID());
        Member member = linkedMember(self, Map.of());
        ConsentContext app = new ConsentContext(self, RecorderRole.MEMBER, ConsentChannel.APP, true);

        member.changeContact(change(ContactField.MOBILE, FieldChange.share("600000001")), Optional.of(app), NOW);

        assertThat(member.contact()).containsEntry(ContactField.MOBILE, "600000001");
        assertThat(member.consents().get(0).channel()).isEqualTo(ConsentChannel.APP);
        assertThat(member.consents().get(0).recorderRole()).isEqualTo(RecorderRole.MEMBER);
    }

    @Test
    void renameReplacesOnlyTheNameAndKeepsContactConsentAndLink() {
        UserId user = new UserId(UUID.randomUUID());
        Member member = Member.restore(new MemberId(UUID.randomUUID()), COMMUNITY, NAME,
                Map.of(ContactField.MOBILE, "600000001"), List.of(), Optional.of(user));

        member.rename(PersonName.of("Beatriz", null));

        assertThat(member.name().givenName()).isEqualTo("Beatriz");
        assertThat(member.name().surnames()).isEmpty();
        assertThat(member.contact()).containsEntry(ContactField.MOBILE, "600000001");
        assertThat(member.linkedUserId()).contains(user);
    }

    @Test
    void consentRecordsCarryNoContactValues() {
        Member member = memberSharing(ContactField.EMAIL, "ana@example.org");

        assertThat(member.consents().toString()).doesNotContain("ana@example.org");
    }

    @Test
    void aConsentContextRejectsAChannelThatDoesNotMatchTheRole() {
        assertThatThrownBy(() -> new ConsentContext(MODERATOR, RecorderRole.MEMBER, ConsentChannel.VERBAL, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsentContext(MODERATOR, RecorderRole.MODERATOR, ConsentChannel.APP, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contactIsNotModifiableFromOutside() {
        Member member = memberSharing(ContactField.MOBILE, "600000001");

        assertThatThrownBy(() -> member.contact().put(ContactField.EMAIL, "x@y.es"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new EnumMap<>(member.contact())).hasSize(1);
    }
}
