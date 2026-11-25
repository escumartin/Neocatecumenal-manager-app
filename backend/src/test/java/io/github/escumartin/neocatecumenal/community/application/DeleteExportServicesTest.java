package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.in.ConsentRecorder;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberDataExport;
import io.github.escumartin.neocatecumenal.community.application.service.DeleteMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.ExportMemberDataService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityForbidden;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ConsentDecision;
import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.RecorderRole;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteExportServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-11-20T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final DeleteMemberService delete = new DeleteMemberService(communities, members, users);
    private final ExportMemberDataService export = new ExportMemberDataService(communities, members, users,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Community home = community();
    private final Community other = community();
    private final Actor administrator = actor(ActorRole.ADMINISTRATOR, home);
    private final Actor moderator = actor(ActorRole.MODERATOR, home);

    DeleteExportServicesTest() {
        users.log = communities.events;
        members.log = communities.events;
    }

    private Community community() {
        return communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(communities.stored.size() + 1), Optional.empty(), CommunityStatus.ACTIVE,
                Optional.empty()));
    }

    private static Actor actor(ActorRole role, Community community) {
        return new Actor(new UserId(UUID.randomUUID()), role, community.id());
    }

    private Member unlinkedMemberIn(Community community) {
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), community.id(),
                PersonName.of("Luis", "Garcia"), Map.of(), List.of(), Optional.empty()));
    }

    private LinkedUser userIn(Community community, ActorRole role) {
        LinkedUser user = new LinkedUser(new UserId(UUID.randomUUID()), role, community.id(), "Other Name");
        users.users.add(user);
        return user;
    }

    private Member linkedMemberIn(Community community, ActorRole role) {
        LinkedUser user = userIn(community, role);
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), community.id(),
                PersonName.of("Luis", "Garcia"), Map.of(), List.of(), Optional.of(user.id())));
    }

    private Member memberLinkedTo(Community community, UserId userId) {
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), community.id(),
                PersonName.of("Luis", "Garcia"), Map.of(), List.of(), Optional.of(userId)));
    }

    private void makeResponsible(Community community, Member member) {
        communities.stored.put(community.id(), Community.restore(community.id(), community.number(),
                Optional.empty(), community.status(), Optional.of(member.id())));
    }

    private static ConsentRecord consent(ContactField field, ConsentDecision decision, ConsentChannel channel,
            UserId by, RecorderRole role, Instant at) {
        return new ConsentRecord(UUID.randomUUID(), field, decision, channel, by, role, true, at);
    }

    private Member memberWithConsents(Community community, UserId linked, ConsentRecord... records) {
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), community.id(),
                PersonName.of("Luis", "Garcia"), Map.of(ContactField.MOBILE, "600000001"), List.of(records),
                Optional.ofNullable(linked)));
    }

    @Test
    void moderatorDeletesAnUnlinkedMemberOfTheirCommunity() {
        Member member = unlinkedMemberIn(home);

        delete.delete(moderator, member.id());

        assertThat(members.stored).doesNotContainKey(member.id());
        assertThat(users.deleted).isEmpty();
    }

    @Test
    void deletionLocksTheCommunityThenTheMemberThenTheUserAndDeletesTheUserBeforeTheMember() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);

        delete.delete(moderator, member.id());

        assertThat(communities.events).containsExactly("lock", "member-lock", "user-lock", "user-delete",
                "member-delete");
        assertThat(communities.lockCalls).containsExactly(List.of(home.id()));
        assertThat(users.deleted).containsExactly(member.linkedUserId().orElseThrow());
    }

    @Test
    void moderatorDeletesAMemberLinkedToARegisteredUserAndTheUserWithIt() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);

        delete.delete(moderator, member.id());

        assertThat(members.stored).doesNotContainKey(member.id());
        assertThat(users.deleted).containsExactly(member.linkedUserId().orElseThrow());
    }

    @Test
    void moderatorDeletingTheirOwnMemberIsForbidden() {
        Member own = memberLinkedTo(home, moderator.id());
        users.users.add(new LinkedUser(moderator.id(), ActorRole.MODERATOR, home.id(), "Moderator"));

        assertThatThrownBy(() -> delete.delete(moderator, own.id())).isInstanceOf(CommunityForbidden.class);
        assertThat(members.stored).containsKey(own.id());
        assertThat(users.deleted).isEmpty();
    }

    @Test
    void moderatorDeletingAMemberLinkedToAModeratorIsForbidden() {
        Member member = linkedMemberIn(home, ActorRole.MODERATOR);

        assertThatThrownBy(() -> delete.delete(moderator, member.id())).isInstanceOf(CommunityForbidden.class);
        assertThat(members.stored).containsKey(member.id());
    }

    @Test
    void moderatorDeletingAMemberLinkedToAnAdministratorIsForbidden() {
        Member member = linkedMemberIn(home, ActorRole.ADMINISTRATOR);

        assertThatThrownBy(() -> delete.delete(moderator, member.id())).isInstanceOf(CommunityForbidden.class);
        assertThat(users.deleted).isEmpty();
    }

    @Test
    void moderatorDeletingAMemberOfAnotherCommunityGetsNotFound() {
        Member foreign = unlinkedMemberIn(other);

        assertThatThrownBy(() -> delete.delete(moderator, foreign.id())).isInstanceOf(MemberNotFound.class);
        assertThat(members.stored).containsKey(foreign.id());
    }

    @Test
    void registeredUserCannotDelete() {
        Member member = unlinkedMemberIn(home);

        assertThatThrownBy(() -> delete.delete(actor(ActorRole.REGISTERED, home), member.id()))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void deletingAMissingMemberGetsNotFound() {
        assertThatThrownBy(() -> delete.delete(moderator, new MemberId(UUID.randomUUID())))
                .isInstanceOf(MemberNotFound.class);
    }

    @Test
    void administratorDeletesAMemberLinkedToAModeratorOfAnyCommunity() {
        Member member = linkedMemberIn(other, ActorRole.MODERATOR);

        delete.delete(administrator, member.id());

        assertThat(members.stored).doesNotContainKey(member.id());
        assertThat(users.deleted).containsExactly(member.linkedUserId().orElseThrow());
        assertThat(communities.lockCalls).containsExactly(List.of(other.id()));
    }

    @Test
    void deletingTheResponsibleMemberIsRejectedWithoutTouchingIdentity() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        makeResponsible(home, member);

        assertThatThrownBy(() -> delete.delete(moderator, member.id())).isInstanceOfSatisfying(
                CommunityDomainException.class, e -> assertThat(e.code()).isEqualTo("RESPONSIBLE_REQUIRED"));
        assertThat(members.stored).containsKey(member.id());
        assertThat(users.deleted).isEmpty();
        assertThat(communities.events).doesNotContain("user-delete", "member-delete");
    }

    @Test
    void identityRejectionLeavesTheMemberInPlace() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        users.failOnDelete = new IdentityRuleViolation("SELF_ACTION_FORBIDDEN");

        assertThatThrownBy(() -> delete.delete(administrator, member.id())).isInstanceOfSatisfying(
                IdentityRuleViolation.class, e -> assertThat(e.code()).isEqualTo("SELF_ACTION_FORBIDDEN"));
        assertThat(members.stored).containsKey(member.id());
        assertThat(communities.events).doesNotContain("member-delete");
    }

    @Test
    void lastModeratorAndLastAdministratorRulesComeFromIdentity() {
        Member member = linkedMemberIn(home, ActorRole.MODERATOR);

        for (String code : List.of("LAST_MODERATOR", "LAST_ADMINISTRATOR")) {
            users.failOnDelete = new IdentityRuleViolation(code);
            assertThatThrownBy(() -> delete.delete(administrator, member.id())).isInstanceOfSatisfying(
                    IdentityRuleViolation.class, e -> assertThat(e.code()).isEqualTo(code));
        }
        assertThat(members.stored).containsKey(member.id());
    }

    @Test
    void deletionRequestsARetryWhenTheMemberChangedCommunityBeforeItsLock() {
        Member member = unlinkedMemberIn(home);
        members.lockResult = Optional.of(Member.restore(member.id(), other.id(), member.name(), Map.of(),
                List.of(), Optional.empty()));

        assertThatThrownBy(() -> delete.delete(moderator, member.id())).isInstanceOf(StaleLockOrderException.class);
        assertThat(members.stored).containsKey(member.id());
    }

    @Test
    void deletionOfAMemberWhoseLinkWasReleasedWhileWaitingOnlyDeletesTheMember() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        members.lockResult = Optional.of(Member.restore(member.id(), home.id(), member.name(), Map.of(),
                List.of(), Optional.empty()));

        delete.delete(moderator, member.id());

        assertThat(users.deleted).isEmpty();
        assertThat(members.stored).doesNotContainKey(member.id());
    }

    @Test
    void exportContainsEverythingStoredAndNothingElse() {
        UserId recorder = userIn(home, ActorRole.MODERATOR).id();
        Member member = memberWithConsents(home, null,
                consent(ContactField.MOBILE, ConsentDecision.GRANTED, ConsentChannel.VERBAL, recorder,
                        RecorderRole.MODERATOR, EARLIER));

        MemberDataExport result = export.export(moderator, member.id());

        assertThat(result.member().member()).isEqualTo(member);
        assertThat(result.communityNumber()).isEqualTo(home.number().value());
        assertThat(result.exportedAt()).isEqualTo(NOW);
        assertThat(result.consentRecords()).hasSize(1);
        assertThat(result.consentRecords().get(0).record()).isEqualTo(member.consents().get(0));
    }

    @Test
    void exportResolvesTheRecorderNameAndRole() {
        LinkedUser recorder = userIn(home, ActorRole.MODERATOR);
        Member member = memberWithConsents(home, null,
                consent(ContactField.MOBILE, ConsentDecision.GRANTED, ConsentChannel.WRITTEN, recorder.id(),
                        RecorderRole.MODERATOR, EARLIER));

        MemberDataExport result = export.export(administrator, member.id());

        assertThat(result.consentRecords().get(0).recordedBy())
                .isEqualTo(new ConsentRecorder(RecorderRole.MODERATOR, Optional.of("Other Name"), false));
    }

    @Test
    void exportMarksADeletedRecorderWithoutNameAndAMemberRecorderWithoutName() {
        UserId gone = new UserId(UUID.randomUUID());
        UserId own = userIn(home, ActorRole.REGISTERED).id();
        Member member = memberWithConsents(home, own,
                consent(ContactField.MOBILE, ConsentDecision.GRANTED, ConsentChannel.VERBAL, gone,
                        RecorderRole.MODERATOR, EARLIER),
                consent(ContactField.EMAIL, ConsentDecision.GRANTED, ConsentChannel.APP, own, RecorderRole.MEMBER,
                        NOW));

        MemberDataExport result = export.export(moderator, member.id());

        assertThat(result.consentRecords()).extracting(consent -> consent.recordedBy()).containsExactly(
                new ConsentRecorder(RecorderRole.MODERATOR, Optional.empty(), true),
                new ConsentRecorder(RecorderRole.MEMBER, Optional.empty(), false));
    }

    @Test
    void exportForAMemberWithoutConsentsHasAnEmptyRecord() {
        Member member = unlinkedMemberIn(home);

        assertThat(export.export(moderator, member.id()).consentRecords()).isEmpty();
    }

    @Test
    void administratorExportsAMemberOfAnyCommunity() {
        Member member = unlinkedMemberIn(other);

        assertThat(export.export(administrator, member.id()).communityNumber()).isEqualTo(other.number().value());
    }

    @Test
    void moderatorExportingAMemberOfAnotherCommunityGetsNotFound() {
        Member foreign = unlinkedMemberIn(other);

        assertThatThrownBy(() -> export.export(moderator, foreign.id())).isInstanceOf(MemberNotFound.class);
    }

    @Test
    void registeredUserCannotExport() {
        Member member = unlinkedMemberIn(home);

        assertThatThrownBy(() -> export.export(actor(ActorRole.REGISTERED, home), member.id()))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void exportOfAMissingMemberGetsNotFound() {
        assertThatThrownBy(() -> export.export(moderator, new MemberId(UUID.randomUUID())))
                .isInstanceOf(MemberNotFound.class);
    }
}
