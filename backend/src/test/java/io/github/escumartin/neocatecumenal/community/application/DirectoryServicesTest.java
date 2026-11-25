package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.service.GetMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.ListMembersService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityForbidden;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.ConsentContext;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DirectoryServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final GetMemberService get = new GetMemberService(communities, members);
    private final ListMembersService list = new ListMembersService(communities, members);
    private final Community own = community(CommunityStatus.ACTIVE);
    private final Community other = community(CommunityStatus.ACTIVE);

    private Community community(CommunityStatus status) {
        return communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(communities.stored.size() + 1), Optional.empty(), status, Optional.empty()));
    }

    private record Deactivated(Community community, Member member) {
    }

    private Deactivated deactivatedWithMember() {
        Community community = community(CommunityStatus.ACTIVE);
        Member member = memberIn(community, "Ana", "Pérez");
        community.deactivate(false, true);
        return new Deactivated(community, member);
    }

    private Actor actor(ActorRole role, Community community) {
        return new Actor(new UserId(UUID.randomUUID()), role, community.id());
    }

    private Member memberIn(Community community, String given, String surnames) {
        return members.add(Member.register(new MemberId(UUID.randomUUID()), community,
                PersonName.of(given, surnames), Optional.empty(), Optional.empty(), NOW));
    }

    private Member memberWithMobile(Community community, String given, String mobile) {
        Actor moderator = actor(ActorRole.MODERATOR, community);
        return members.add(Member.register(new MemberId(UUID.randomUUID()), community, PersonName.of(given, null),
                Optional.of(ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share(mobile)))),
                Optional.of(ConsentContext.onBehalf(moderator, new OnBehalfConsent(ConsentChannel.VERBAL, true))),
                NOW));
    }

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    void everyRoleListsTheDirectoryOfTheirOwnCommunityInDirectoryOrder(ActorRole role) {
        Member zapata = memberIn(own, "Ana", "Zapata");
        Member alvarez = memberIn(own, "Luis", "Álvarez");
        memberIn(other, "Pedro", "Aguirre");

        List<MemberView> listed = list.list(actor(role, own), own.id());

        assertThat(listed).extracting(view -> view.member().id()).containsExactly(alvarez.id(), zapata.id());
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void listingAnotherCommunityIsForbiddenForRegisteredUsersAndModerators(ActorRole role) {
        assertThatThrownBy(() -> list.list(actor(role, own), other.id())).isInstanceOf(CommunityForbidden.class);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void listingADeactivatedOwnCommunityFailsForRegisteredUsersAndModerators(ActorRole role) {
        Community deactivated = deactivatedWithMember().community();

        assertThatThrownBy(() -> list.list(actor(role, deactivated), deactivated.id()))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED"));
    }

    @Test
    void administratorListsADeactivatedCommunity() {
        Community deactivated = deactivatedWithMember().community();

        assertThat(list.list(actor(ActorRole.ADMINISTRATOR, own), deactivated.id())).hasSize(1);
    }

    @Test
    void administratorListsAnyCommunity() {
        memberIn(other, "Ana", "Pérez");

        assertThat(list.list(actor(ActorRole.ADMINISTRATOR, own), other.id())).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    void everyRoleGetsAMemberOfTheirOwnCommunity(ActorRole role) {
        Member member = memberIn(own, "Ana", "Pérez");

        assertThat(get.get(actor(role, own), member.id()).member()).isSameAs(member);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void aMemberOfAnotherCommunityAnswersNotFoundJustLikeAMissingOne(ActorRole role) {
        Member foreign = memberIn(other, "Luis", "Gil");
        Actor actor = actor(role, own);

        assertThatThrownBy(() -> get.get(actor, foreign.id())).isInstanceOf(MemberNotFound.class);
        assertThatThrownBy(() -> get.get(actor, new MemberId(UUID.randomUUID())))
                .isInstanceOf(MemberNotFound.class);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void aMemberOfADeactivatedOwnCommunityFailsForRegisteredUsersAndModerators(ActorRole role) {
        Deactivated fixture = deactivatedWithMember();
        Community deactivated = fixture.community();
        Member member = fixture.member();

        assertThatThrownBy(() -> get.get(actor(role, deactivated), member.id()))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED"));
    }

    @Test
    void administratorGetsAMemberOfADeactivatedCommunity() {
        Deactivated fixture = deactivatedWithMember();
        Community deactivated = fixture.community();
        Member member = fixture.member();

        assertThat(get.get(actor(ActorRole.ADMINISTRATOR, own), member.id()).member()).isSameAs(member);
    }

    @ParameterizedTest
    @EnumSource(ActorRole.class)
    void everyRoleSeesTheSameConsentedContactFields(ActorRole role) {
        Member member = memberWithMobile(own, "Ana", "600000001");

        assertThat(get.get(actor(role, own), member.id()).member().contact().get(ContactField.MOBILE))
                .isEqualTo("600000001");
        assertThat(list.list(actor(role, own), own.id()).getFirst().member().contact().get(ContactField.MOBILE))
                .isEqualTo("600000001");
    }
}
