package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.service.GuardUserLinkService;
import io.github.escumartin.neocatecumenal.community.application.service.LinkMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.TransferMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.UnlinkMemberService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityForbidden;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LinkTransferServicesTest {

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final LinkMemberService link = new LinkMemberService(communities, members, users);
    private final UnlinkMemberService unlink = new UnlinkMemberService(communities, members, users);
    private final TransferMemberService transfer = new TransferMemberService(communities, members, users);
    private final GuardUserLinkService guard = new GuardUserLinkService(communities, members);
    private final Community home = community(CommunityStatus.ACTIVE);
    private final Community other = community(CommunityStatus.ACTIVE);
    private final Actor administrator = actor(ActorRole.ADMINISTRATOR, home);
    private final Actor moderator = actor(ActorRole.MODERATOR, home);

    LinkTransferServicesTest() {
        users.log = communities.events;
        members.log = communities.events;
    }

    private Community community(CommunityStatus status) {
        return communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(communities.stored.size() + 1), Optional.empty(), status, Optional.empty()));
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

    private void makeResponsible(Community community, Member member) {
        communities.stored.put(community.id(), Community.restore(community.id(), community.number(),
                Optional.empty(), community.status(), Optional.of(member.id())));
    }

    private void moveMemberTo(Member member, Community community) {
        members.stored.put(member.id(), Member.restore(member.id(), community.id(), member.name(), Map.of(),
                List.of(), member.linkedUserId()));
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void linkLocksTheCommunityThenTheMemberThenTheUser() {
        Member member = unlinkedMemberIn(home);
        LinkedUser user = userIn(home, ActorRole.REGISTERED);

        link.link(moderator, member.id(), user.id());

        assertThat(communities.events).containsExactly("lock", "member-lock", "user-lock", "member-save");
        assertThat(communities.lockCalls).containsExactly(List.of(home.id()));
        assertThat(members.stored.get(member.id()).linkedUserId()).contains(user.id());
    }

    @Test
    void linkWithAMissingUserFailsOnTheUserFieldAndSavesNothing() {
        Member member = unlinkedMemberIn(home);

        assertThatThrownBy(() -> link.link(moderator, member.id(), new UserId(UUID.randomUUID())))
                .isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
                    assertThat(e.field()).isEqualTo("userId");
                    assertThat(e.code()).isEqualTo("USER_NOT_FOUND");
                });
        assertThat(communities.events).doesNotContain("member-save");
    }

    @Test
    void moderatorLinksARegisteredUserOfTheirCommunity() {
        Member member = unlinkedMemberIn(home);
        LinkedUser user = userIn(home, ActorRole.REGISTERED);

        assertThat(link.link(moderator, member.id(), user.id()).member().linkedUserId()).contains(user.id());
    }

    @Test
    void moderatorLinkingAModeratorIsForbidden() {
        Member member = unlinkedMemberIn(home);
        LinkedUser staff = userIn(home, ActorRole.MODERATOR);

        assertThatThrownBy(() -> link.link(moderator, member.id(), staff.id())).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void moderatorLinkingAnAdministratorIsForbidden() {
        Member member = unlinkedMemberIn(home);
        LinkedUser staff = userIn(home, ActorRole.ADMINISTRATOR);

        assertThatThrownBy(() -> link.link(moderator, member.id(), staff.id())).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void moderatorLinkingAUserOfAnotherCommunityIsForbidden() {
        Member member = unlinkedMemberIn(home);
        LinkedUser foreign = userIn(other, ActorRole.REGISTERED);

        assertThatThrownBy(() -> link.link(moderator, member.id(), foreign.id()))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void administratorLinkingAUserOfAnotherCommunityFailsOnTheUserField() {
        Member member = unlinkedMemberIn(home);
        LinkedUser foreign = userIn(other, ActorRole.REGISTERED);

        assertThatThrownBy(() -> link.link(administrator, member.id(), foreign.id()))
                .isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
                    assertThat(e.field()).isEqualTo("userId");
                    assertThat(e.code()).isEqualTo("USER_NOT_IN_COMMUNITY");
                });
    }

    @Test
    void linkingAMemberOfAnotherCommunityLooksLikeAMissingOneForAModerator() {
        Member foreign = unlinkedMemberIn(other);
        LinkedUser user = userIn(home, ActorRole.REGISTERED);

        assertThatThrownBy(() -> link.link(moderator, foreign.id(), user.id())).isInstanceOf(MemberNotFound.class);
    }

    @Test
    void registeredUsersCannotLink() {
        Member member = unlinkedMemberIn(home);
        LinkedUser user = userIn(home, ActorRole.REGISTERED);

        assertThatThrownBy(() -> link.link(actor(ActorRole.REGISTERED, home), member.id(), user.id()))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void linkingAMemberThatIsAlreadyLinkedFails() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        LinkedUser user = userIn(home, ActorRole.REGISTERED);

        assertRule(() -> link.link(moderator, member.id(), user.id()), "MEMBER_ALREADY_LINKED");
    }

    @Test
    void linkRequestsARetryWhenTheMemberMovedBetweenTheReadAndTheLock() {
        Member member = unlinkedMemberIn(home);
        LinkedUser user = userIn(home, ActorRole.REGISTERED);
        communities.onLock = () -> moveMemberTo(member, other);

        assertThatThrownBy(() -> link.link(administrator, member.id(), user.id()))
                .isInstanceOf(StaleLockOrderException.class);
        assertThat(communities.events).doesNotContain("user-lock", "member-save");
    }

    @Test
    void unlinkLocksTheCommunityThenTheMemberAndClearsTheLink() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);

        unlink.unlink(moderator, member.id());

        assertThat(communities.events).containsExactly("lock", "member-lock", "member-save");
        assertThat(members.stored.get(member.id()).linkedUserId()).isEmpty();
    }

    @Test
    void administratorUnlinksAMemberLinkedToAModerator() {
        Member member = linkedMemberIn(home, ActorRole.MODERATOR);

        unlink.unlink(administrator, member.id());

        assertThat(members.stored.get(member.id()).linkedUserId()).isEmpty();
    }

    @Test
    void moderatorUnlinkingAMemberLinkedToAModeratorIsForbidden() {
        Member member = linkedMemberIn(home, ActorRole.MODERATOR);

        assertThatThrownBy(() -> unlink.unlink(moderator, member.id())).isInstanceOf(CommunityForbidden.class);
        assertThat(members.stored.get(member.id()).linkedUserId()).isPresent();
    }

    @Test
    void theLinkedRegisteredUserCannotUnlinkThemselves() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        Actor self = new Actor(member.linkedUserId().orElseThrow(), ActorRole.REGISTERED, home.id());

        assertThatThrownBy(() -> unlink.unlink(self, member.id())).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void unlinkingAMemberWithoutUserFailsForAModerator() {
        Member member = unlinkedMemberIn(home);

        assertRule(() -> unlink.unlink(moderator, member.id()), "MEMBER_NOT_LINKED");
    }

    @Test
    void transferLocksBothCommunitiesThenTheMemberThenMovesTheUser() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);

        transfer.transfer(administrator, member.id(), other.id());

        assertThat(communities.events).containsExactly("lock", "member-lock", "member-save", "user-move");
        assertThat(communities.lockCalls).containsExactly(List.of(home.id(), other.id()));
        assertThat(members.stored.get(member.id()).communityId()).isEqualTo(other.id());
        assertThat(users.moves).containsExactly(new FakeUserDirectory.Move(member.linkedUserId().orElseThrow(),
                other.id()));
    }

    @Test
    void transferOfAMemberWithoutUserDoesNotTouchIdentity() {
        Member member = unlinkedMemberIn(home);

        transfer.transfer(administrator, member.id(), other.id());

        assertThat(members.stored.get(member.id()).communityId()).isEqualTo(other.id());
        assertThat(users.moves).isEmpty();
        assertThat(users.locked).isEmpty();
    }

    @Test
    void onlyAdministratorsTransfer() {
        Member member = unlinkedMemberIn(home);

        assertThatThrownBy(() -> transfer.transfer(moderator, member.id(), other.id()))
                .isInstanceOf(CommunityForbidden.class);
        assertThat(communities.events).isEmpty();
    }

    @Test
    void transferToAMissingCommunityIsNotFound() {
        Member member = unlinkedMemberIn(home);

        assertThatThrownBy(() -> transfer.transfer(administrator, member.id(),
                new CommunityId(UUID.randomUUID()))).isInstanceOf(CommunityNotFound.class);
    }

    @Test
    void transferOfAMissingMemberIsNotFound() {
        assertThatThrownBy(() -> transfer.transfer(administrator, new MemberId(UUID.randomUUID()), other.id()))
                .isInstanceOf(MemberNotFound.class);
    }

    @Test
    void transferToADeactivatedCommunityFailsWithoutSavingOrMovingTheUser() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        Community deactivated = community(CommunityStatus.DEACTIVATED);

        assertRule(() -> transfer.transfer(administrator, member.id(), deactivated.id()), "COMMUNITY_DEACTIVATED");
        assertThat(communities.events).doesNotContain("member-save", "user-move");
    }

    @Test
    void transferUsesTheDestinationStatusReadAfterTheLock() {
        Member member = unlinkedMemberIn(home);
        communities.onLock = () -> communities.stored.put(other.id(), Community.restore(other.id(), other.number(),
                Optional.empty(), CommunityStatus.DEACTIVATED, Optional.empty()));

        assertRule(() -> transfer.transfer(administrator, member.id(), other.id()), "COMMUNITY_DEACTIVATED");
        assertThat(communities.events).doesNotContain("member-save");
    }

    @Test
    void transferOfTheResponsibleFailsAgainstTheLockedSourceCommunity() {
        Member member = unlinkedMemberIn(home);
        communities.onLock = () -> makeResponsible(home, member);

        assertRule(() -> transfer.transfer(administrator, member.id(), other.id()), "RESPONSIBLE_REQUIRED");
        assertThat(communities.events).doesNotContain("member-save");
    }

    @Test
    void transferRequestsARetryWhenTheMemberLeftTheSourceBeforeTheMemberLock() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        Community third = community(CommunityStatus.ACTIVE);
        communities.onLock = () -> moveMemberTo(member, third);

        assertThatThrownBy(() -> transfer.transfer(administrator, member.id(), other.id()))
                .isInstanceOf(StaleLockOrderException.class);
        assertThat(communities.events).doesNotContain("member-save", "user-move");
    }

    @Test
    void transferPropagatesTheIdentityRuleThatRejectsTheUserMove() {
        Member member = linkedMemberIn(home, ActorRole.MODERATOR);
        users.failOnMove = new IdentityRuleViolation("LAST_MODERATOR");

        assertThatThrownBy(() -> transfer.transfer(administrator, member.id(), other.id()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class, e -> assertThat(e.code())
                        .isEqualTo("LAST_MODERATOR"));
    }

    @Test
    void guardAllowsTheChangeOfAUserWithoutMember() {
        UserId user = new UserId(UUID.randomUUID());

        assertThat(guard.allowsCommunityChange(user, home.id(), other.id())).isTrue();
        assertThat(communities.lockCalls).containsExactly(List.of(home.id(), other.id()));
    }

    @Test
    void guardBlocksTheChangeWhileTheLinkedMemberStaysInTheCurrentCommunity() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);

        assertThat(guard.allowsCommunityChange(member.linkedUserId().orElseThrow(), home.id(), other.id()))
                .isFalse();
    }

    @Test
    void guardAllowsTheChangeOnceTheLinkedMemberIsInTheTargetCommunity() {
        Member member = linkedMemberIn(other, ActorRole.REGISTERED);

        assertThat(guard.allowsCommunityChange(member.linkedUserId().orElseThrow(), home.id(), other.id()))
                .isTrue();
    }

    @Test
    void guardLocksTheCommunitiesBeforeTheMember() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);

        guard.allowsCommunityChange(member.linkedUserId().orElseThrow(), home.id(), other.id());

        assertThat(communities.events).containsExactly("lock", "member-lock");
    }

    @Test
    void guardRequestsARetryWhenTheLinkedMemberIsInNeitherLockedCommunity() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        Community third = community(CommunityStatus.ACTIVE);
        members.lockResult = Optional.of(Member.restore(member.id(), third.id(), member.name(), Map.of(),
                List.of(), member.linkedUserId()));

        assertThatThrownBy(() -> guard.allowsCommunityChange(member.linkedUserId().orElseThrow(), home.id(),
                other.id())).isInstanceOf(StaleLockOrderException.class);
    }

    @Test
    void releaseUnlinksTheMemberAfterLockingTheCommunity() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);

        guard.releaseBeforeDeletion(member.linkedUserId().orElseThrow(), home.id());

        assertThat(communities.events).containsExactly("lock", "member-lock", "member-save");
        assertThat(members.stored.get(member.id()).linkedUserId()).isEmpty();
        assertThat(members.stored.get(member.id()).name()).isEqualTo(member.name());
    }

    @Test
    void releaseLogsNoIdentifierOfTheUnlinkedMember() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        ch.qos.logback.classic.Logger serviceLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(io.github.escumartin.neocatecumenal.community.application.service.GuardUserLinkService.class);
        appender.start();
        serviceLogger.addAppender(appender);
        try {
            guard.releaseBeforeDeletion(member.linkedUserId().orElseThrow(), home.id());
        } finally {
            serviceLogger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs()).isNullOrEmpty();
        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain(member.id().value().toString());
    }

    @Test
    void releaseOfAUserWithoutMemberOnlyLocksTheCommunity() {
        guard.releaseBeforeDeletion(new UserId(UUID.randomUUID()), home.id());

        assertThat(communities.events).containsExactly("lock");
    }

    @Test
    void releaseLocksTheCommunityOfTheUserAndOfTheLinkedMember() {
        Member member = linkedMemberIn(other, ActorRole.REGISTERED);

        guard.releaseBeforeDeletion(member.linkedUserId().orElseThrow(), home.id());

        assertThat(communities.lockCalls).containsExactly(List.of(home.id(), other.id()));
        assertThat(members.stored.get(member.id()).linkedUserId()).isEmpty();
    }

    @Test
    void releaseRequestsARetryWhenTheMemberLeftTheLockedCommunitiesBeforeItsLock() {
        Member member = linkedMemberIn(home, ActorRole.REGISTERED);
        Community third = community(CommunityStatus.ACTIVE);
        members.lockResult = Optional.of(Member.restore(member.id(), third.id(), member.name(), Map.of(),
                List.of(), member.linkedUserId()));

        assertThatThrownBy(() -> guard.releaseBeforeDeletion(member.linkedUserId().orElseThrow(), home.id()))
                .isInstanceOf(StaleLockOrderException.class);
        assertThat(communities.events).doesNotContain("member-save");
    }

    @Test
    void releaseRequestsARetryWhenALinkAppearsInAnotherCommunityAfterTheLock() {
        UserId user = new UserId(UUID.randomUUID());
        communities.onLock = () -> members.add(Member.restore(new MemberId(UUID.randomUUID()), other.id(),
                PersonName.of("Luis", "Garcia"), Map.of(), List.of(), Optional.of(user)));

        assertThatThrownBy(() -> guard.releaseBeforeDeletion(user, home.id()))
                .isInstanceOf(StaleLockOrderException.class);
    }

    @Test
    void releaseUnlinksALinkThatAppearsInTheLockedCommunityAfterTheLock() {
        UserId user = new UserId(UUID.randomUUID());
        Member late = Member.restore(new MemberId(UUID.randomUUID()), home.id(), PersonName.of("Luis", "Garcia"),
                Map.of(), List.of(), Optional.of(user));
        communities.onLock = () -> members.add(late);

        guard.releaseBeforeDeletion(user, home.id());

        assertThat(members.stored.get(late.id()).linkedUserId()).isEmpty();
    }
}
