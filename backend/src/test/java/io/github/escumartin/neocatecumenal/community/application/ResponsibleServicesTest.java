package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.in.CommunityView;
import io.github.escumartin.neocatecumenal.community.application.service.DesignateResponsibleService;
import io.github.escumartin.neocatecumenal.community.application.service.GetCommunityService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResponsibleServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final DesignateResponsibleService designate = new DesignateResponsibleService(communities, members, users);
    private final GetCommunityService get = new GetCommunityService(communities, members, users);
    private final Community own = community();
    private final Community other = community();
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, own.id());

    private Community community() {
        return communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(communities.stored.size() + 1), Optional.empty(), CommunityStatus.ACTIVE,
                Optional.empty()));
    }

    private Member memberIn(Community community, String given) {
        return members.add(Member.register(new MemberId(UUID.randomUUID()), community,
                PersonName.of(given, "Garcia"), Optional.empty(), Optional.empty(), NOW));
    }

    private Member linkedMemberIn(Community community, ActorRole role) {
        UserId userId = new UserId(UUID.randomUUID());
        Member member = members.add(Member.restore(new MemberId(UUID.randomUUID()), community.id(),
                PersonName.of("Luis", "Garcia"), java.util.Map.of(), java.util.List.of(), Optional.of(userId)));
        users.users.add(new LinkedUser(userId, role, community.id()));
        return member;
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void designateLocksTheCommunityBeforeTheMember() {
        Member member = memberIn(own, "Ana");
        members.log = communities.events;

        designate.designate(administrator, own.id(), member.id());

        assertThat(communities.events).containsExactly("lock", "member-lock", "save");
        assertThat(communities.lockCalls).containsExactly(java.util.List.of(own.id()));
    }

    @Test
    void designateMakesAMemberOfTheCommunityTheResponsible() {
        Member member = memberIn(own, "Ana");

        CommunityView view = designate.designate(administrator, own.id(), member.id());

        assertThat(communities.stored.get(own.id()).isResponsible(member.id())).isTrue();
        assertThat(view.responsible()).contains(member);
    }

    @Test
    void designateReplacesTheCurrentResponsible() {
        Member first = memberIn(own, "Ana");
        Member second = memberIn(own, "Berta");
        designate.designate(administrator, own.id(), first.id());

        designate.designate(administrator, own.id(), second.id());

        assertThat(communities.stored.get(own.id()).isResponsible(second.id())).isTrue();
        assertThat(communities.stored.get(own.id()).hasResponsible()).isTrue();
    }

    @Test
    void designateRejectsAMemberOfAnotherCommunityAndSavesNothing() {
        Member foreign = memberIn(other, "Carla");

        assertRule(() -> designate.designate(administrator, own.id(), foreign.id()), "MEMBER_NOT_IN_COMMUNITY");

        assertThat(communities.events).doesNotContain("save");
        assertThat(communities.stored.get(own.id()).hasResponsible()).isFalse();
    }

    @Test
    void designateRejectsAnUnknownMember() {
        assertThatThrownBy(() -> designate.designate(administrator, own.id(), new MemberId(UUID.randomUUID())))
                .isInstanceOf(MemberNotFound.class);
    }

    @Test
    void designateRejectsAnUnknownCommunity() {
        assertThatThrownBy(() -> designate.designate(administrator, new CommunityId(UUID.randomUUID()),
                new MemberId(UUID.randomUUID()))).isInstanceOf(CommunityNotFound.class);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void designateIsForbiddenForNonAdministratorsWithoutLockingAnything(ActorRole role) {
        Member member = memberIn(own, "Ana");
        Actor actor = new Actor(new UserId(UUID.randomUUID()), role, own.id());

        assertThatThrownBy(() -> designate.designate(actor, own.id(), member.id()))
                .isInstanceOf(CommunityForbidden.class);

        assertThat(communities.lockCalls).isEmpty();
        assertThat(communities.events).doesNotContain("save");
    }

    @Test
    void getReportsACommunityWithoutResponsibleAsSuch() {
        CommunityView view = get.get(administrator, own.id());

        assertThat(view.responsible()).isEmpty();
        assertThat(view.responsibleIsModerator()).isFalse();
        assertThat(view.community().hasResponsible()).isFalse();
    }

    @Test
    void getReportsTheResponsibleIsAModeratorWhenTheLinkedUserIsAModerator() {
        Member responsible = linkedMemberIn(own, ActorRole.MODERATOR);
        own.designateResponsible(responsible);

        CommunityView view = get.get(administrator, own.id());

        assertThat(view.responsible()).contains(responsible);
        assertThat(view.responsibleIsModerator()).isTrue();
        assertThat(users.looked).containsExactly(responsible.linkedUserId().orElseThrow());
    }

    @Test
    void getWarnsWhenTheLinkedUserOfTheResponsibleIsNotAModerator() {
        Member responsible = linkedMemberIn(own, ActorRole.REGISTERED);
        own.designateResponsible(responsible);

        CommunityView view = get.get(administrator, own.id());

        assertThat(view.responsible()).contains(responsible);
        assertThat(view.responsibleIsModerator()).isFalse();
    }

    @Test
    void getWarnsWhenTheResponsibleHasNoLinkedUser() {
        Member responsible = memberIn(own, "Ana");
        own.designateResponsible(responsible);

        CommunityView view = get.get(administrator, own.id());

        assertThat(view.responsibleIsModerator()).isFalse();
        assertThat(users.looked).isEmpty();
    }

    @Test
    void getWarnsWhenTheLinkedUserIsNoLongerInTheDirectory() {
        Member responsible = linkedMemberIn(own, ActorRole.MODERATOR);
        users.users.clear();
        own.designateResponsible(responsible);

        assertThat(get.get(administrator, own.id()).responsibleIsModerator()).isFalse();
    }

    @Test
    void designatingAResponsibleWithoutModeratorRoleIsNotBlocked() {
        Member responsible = linkedMemberIn(own, ActorRole.REGISTERED);

        CommunityView view = designate.designate(administrator, own.id(), responsible.id());

        assertThat(view.community().isResponsible(responsible.id())).isTrue();
        assertThat(view.responsibleIsModerator()).isFalse();
    }
}
