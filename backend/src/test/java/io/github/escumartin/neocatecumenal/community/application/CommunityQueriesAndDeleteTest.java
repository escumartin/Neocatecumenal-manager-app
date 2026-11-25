package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.service.DeleteCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.GetCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.ListCommunitiesService;
import io.github.escumartin.neocatecumenal.community.application.service.ProvisionCommunityService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityForbidden;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityName;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CommunityQueriesAndDeleteTest {

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final RecordingCatalog catalog = new RecordingCatalog();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final FakeMembers members = new FakeMembers();
    private final FakeHistory history = new FakeHistory();
    private final DeleteCommunityService delete = new DeleteCommunityService(communities, members, users, history,
            catalog);
    private final ListCommunitiesService list = new ListCommunitiesService(communities);
    private final GetCommunityService get = new GetCommunityService(communities, members, users);
    private final ProvisionCommunityService provision = new ProvisionCommunityService(communities, catalog);
    private final Actor administrator = actor(ActorRole.ADMINISTRATOR, UUID.randomUUID());

    private static Actor actor(ActorRole role, UUID community) {
        return new Actor(new UserId(UUID.randomUUID()), role, new CommunityId(community));
    }

    private Community existing(int number) {
        return communities.add(Community.create(new CommunityId(UUID.randomUUID()), new CommunityNumber(number),
                CommunityName.of("Community " + number)));
    }

    private Community existingWithResponsible(int number) {
        return communities.add(Community.restore(new CommunityId(UUID.randomUUID()), new CommunityNumber(number),
                Optional.empty(), CommunityStatus.ACTIVE, Optional.of(new MemberId(UUID.randomUUID()))));
    }

    private Community deactivated(int number) {
        Community community = existing(number);
        community.deactivate(false, false);
        return community;
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void deleteLocksTheCommunityThenRemovesItAndItsProjection() {
        Community community = existing(12);

        delete.delete(administrator, community.id());

        assertThat(communities.lockCalls).containsExactly(List.of(community.id()));
        assertThat(communities.events).containsExactly("lock", "delete");
        assertThat(communities.stored).isEmpty();
        assertThat(catalog.calls).containsExactly("remove:" + community.id().value());
    }

    @Test
    void deleteEvaluatesMembersUsersAndHistoryOfTheLockedCommunity() {
        Community community = existing(12);

        delete.delete(administrator, community.id());

        assertThat(members.queried).containsExactly(community.id());
        assertThat(users.queried).containsExactly(community.id());
    }

    @Test
    void deleteIsRejectedWhenThereIsAResponsible() {
        Community community = existingWithResponsible(12);

        assertRule(() -> delete.delete(administrator, community.id()), "COMMUNITY_HAS_RESPONSIBLE");
        assertThat(communities.stored).containsKey(community.id());
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void deleteIsRejectedWhenThereAreMembers() {
        Community community = existing(12);
        members.counts.put(community.id(), 2L);

        assertRule(() -> delete.delete(administrator, community.id()), "COMMUNITY_NOT_EMPTY");
        assertThat(communities.stored).containsKey(community.id());
    }

    @Test
    void deleteIsRejectedWhenThereAreUsers() {
        Community community = existing(12);
        users.users.add(new LinkedUser(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, community.id(), "Test Person"));

        assertRule(() -> delete.delete(administrator, community.id()), "COMMUNITY_NOT_EMPTY");
        assertThat(communities.stored).containsKey(community.id());
    }

    @Test
    void deleteIsRejectedWhenThereIsHistory() {
        Community community = existing(12);
        history.withHistory.add(community.id());

        assertRule(() -> delete.delete(administrator, community.id()), "COMMUNITY_HAS_HISTORY");
        assertThat(communities.stored).containsKey(community.id());
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void deleteIsAllowedFromADeactivatedCommunityWithoutResponsible() {
        Community community = deactivated(12);

        delete.delete(administrator, community.id());

        assertThat(communities.stored).isEmpty();
    }

    @Test
    void deleteOfAnUnknownCommunityIsNotFound() {
        assertThatThrownBy(() -> delete.delete(administrator, new CommunityId(UUID.randomUUID())))
                .isInstanceOf(CommunityNotFound.class);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void deleteIsForbiddenForNonAdministrators(ActorRole role) {
        Community community = existing(12);

        assertThatThrownBy(() -> delete.delete(actor(role, UUID.randomUUID()), community.id()))
                .isInstanceOf(CommunityForbidden.class);
        assertThat(communities.stored).containsKey(community.id());
    }

    @Test
    void listHidesDeactivatedCommunitiesByDefault() {
        Community active = existing(2);
        deactivated(1);

        assertThat(list.list(administrator, false)).containsExactly(active);
    }

    @Test
    void listIncludesDeactivatedCommunitiesForAnAdministratorWhoAsks() {
        Community active = existing(2);
        Community hidden = deactivated(1);

        assertThat(list.list(administrator, true)).containsExactly(hidden, active);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void listReturnsActiveCommunitiesToNonAdministrators(ActorRole role) {
        Community active = existing(2);
        deactivated(1);

        assertThat(list.list(actor(role, active.id().value()), false)).containsExactly(active);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void listWithDeactivatedIsForbiddenForNonAdministrators(ActorRole role) {
        assertThatThrownBy(() -> list.list(actor(role, UUID.randomUUID()), true))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void getExposesWhetherTheCommunityHasAResponsible() {
        Community without = existing(1);
        Community with = existingWithResponsible(2);

        assertThat(get.get(administrator, without.id()).community().hasResponsible()).isFalse();
        assertThat(get.get(administrator, with.id()).community().hasResponsible()).isTrue();
    }

    @Test
    void getLetsAModeratorReadTheirOwnCommunity() {
        Community community = existing(1);

        assertThat(get.get(actor(ActorRole.MODERATOR, community.id().value()), community.id()).community()).isSameAs(community);
    }

    @Test
    void getRejectsAModeratorOfAnotherCommunity() {
        Community community = existing(1);

        assertThatThrownBy(() -> get.get(actor(ActorRole.MODERATOR, UUID.randomUUID()), community.id()))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void getRejectsARegisteredUser() {
        Community community = existing(1);

        assertThatThrownBy(() -> get.get(actor(ActorRole.REGISTERED, community.id().value()), community.id()))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void getOfAnUnknownCommunityIsNotFoundForAnAdministrator() {
        assertThatThrownBy(() -> get.get(administrator, new CommunityId(UUID.randomUUID())))
                .isInstanceOf(CommunityNotFound.class);
    }

    @Test
    void provisionReturnsTheIdOfAnExistingNumberWithoutWritingAnything() {
        Community community = existing(7);

        CommunityId id = provision.ensure(new CommunityNumber(7));

        assertThat(id).isEqualTo(community.id());
        assertThat(catalog.calls).isEmpty();
        assertThat(communities.stored).hasSize(1);
    }

    @Test
    void provisionCreatesTheCommunityAndItsProjectionWhenMissing() {
        CommunityId id = provision.ensure(new CommunityNumber(7));

        assertThat(communities.stored.get(id).number()).isEqualTo(new CommunityNumber(7));
        assertThat(communities.stored.get(id).status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(catalog.calls).containsExactly("register:" + id.value() + ":7");
    }
}
