package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.service.ChangeMemberContactService;
import io.github.escumartin.neocatecumenal.community.application.service.DeactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.DeleteMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.ExportMemberDataService;
import io.github.escumartin.neocatecumenal.community.application.service.LinkMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.ReactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.RenameMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.TransferMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.UnlinkMemberService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DeactivatedCommunityMembersTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final RecordingCatalog catalog = new RecordingCatalog();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RenameMemberService rename = new RenameMemberService(communities, members);
    private final ChangeMemberContactService contact = new ChangeMemberContactService(communities, members, clock);
    private final LinkMemberService link = new LinkMemberService(communities, members, users);
    private final UnlinkMemberService unlink = new UnlinkMemberService(communities, members, users);
    private final DeleteMemberService delete = new DeleteMemberService(communities, members, users);
    private final ExportMemberDataService export = new ExportMemberDataService(communities, members, users, clock);
    private final TransferMemberService transfer = new TransferMemberService(communities, members, users);
    private final DeactivateCommunityService deactivate = new DeactivateCommunityService(communities, users, catalog);
    private final ReactivateCommunityService reactivate = new ReactivateCommunityService(communities, catalog);
    private final Community dormant = communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
            new CommunityNumber(1), Optional.empty(), CommunityStatus.DEACTIVATED, Optional.empty()));
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, dormant.id());
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
            new CommunityId(UUID.randomUUID()));

    private Member member() {
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), dormant.id(),
                PersonName.of("Luis", "Garcia"), Map.of(), List.of(), Optional.empty()));
    }

    private Member linkedMember() {
        LinkedUser user = new LinkedUser(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, dormant.id(), "Test Person");
        users.users.add(user);
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), dormant.id(),
                PersonName.of("Luis", "Garcia"), Map.of(), List.of(), Optional.of(user.id())));
    }

    private static OnBehalfConsent verbal() {
        return new OnBehalfConsent(ConsentChannel.VERBAL, true);
    }

    private static ContactChange mobile() {
        return ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share("600000001")));
    }

    record Operation(String name, java.util.function.BiConsumer<DeactivatedCommunityMembersTest, Actor> run) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Operation> memberOperations() {
        return Stream.of(
                new Operation("rename", (t, actor) -> t.rename.rename(actor, t.member().id(),
                        PersonName.of("Ana", null))),
                new Operation("contact", (t, actor) -> t.contact.change(actor, t.member().id(), mobile(),
                        Optional.of(verbal()))),
                new Operation("unlink", (t, actor) -> t.unlink.unlink(actor, t.linkedMember().id())),
                new Operation("delete", (t, actor) -> t.delete.delete(actor, t.member().id())),
                new Operation("export", (t, actor) -> t.export.export(actor, t.member().id())));
    }

    @ParameterizedTest
    @MethodSource("memberOperations")
    void moderatorOperationsOnADeactivatedCommunityFailWithCommunityDeactivated(Operation operation) {
        assertThatThrownBy(() -> operation.run().accept(this, moderator))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED"));
    }

    @Test
    void moderatorLinkOnADeactivatedCommunityFailsWithCommunityDeactivated() {
        Member member = member();
        LinkedUser user = new LinkedUser(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, dormant.id(), "Test Person");
        users.users.add(user);

        assertThatThrownBy(() -> link.link(moderator, member.id(), user.id()))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED"));
        assertThat(members.stored.get(member.id()).linkedUserId()).isEmpty();
    }

    @Test
    void administratorRenamesAndEditsContactOfAMemberOfADeactivatedCommunity() {
        Member member = member();

        rename.rename(administrator, member.id(), PersonName.of("Ana", null));
        contact.change(administrator, member.id(), mobile(), Optional.of(verbal()));

        assertThat(members.stored.get(member.id()).name().givenName()).isEqualTo("Ana");
        assertThat(members.stored.get(member.id()).contact()).containsKey(ContactField.MOBILE);
    }

    @Test
    void administratorExportsAndDeletesAMemberOfADeactivatedCommunity() {
        Member member = member();

        assertThat(export.export(administrator, member.id()).member().member().id()).isEqualTo(member.id());
        delete.delete(administrator, member.id());

        assertThat(members.stored).doesNotContainKey(member.id());
    }

    @Test
    void transferringAMemberToItsOwnCommunityIsAnUnchangedNoOp() {
        Community active = communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(2), Optional.empty(), CommunityStatus.ACTIVE, Optional.empty()));
        Member member = members.add(Member.restore(new MemberId(UUID.randomUUID()), active.id(),
                PersonName.of("Luis", "Garcia"), Map.of(), List.of(), Optional.empty()));

        assertThat(transfer.transfer(administrator, member.id(), active.id()).member().communityId())
                .isEqualTo(active.id());
        assertThat(users.moves).isEmpty();
    }

    @Test
    void deactivatingADeactivatedCommunityIsANoOpEvenWithPrivilegedUsersUnconfirmed() {
        users.users.add(new LinkedUser(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, dormant.id(), "Test Person"));

        deactivate.deactivate(administrator, dormant.id(), false);

        assertThat(dormant.status()).isEqualTo(CommunityStatus.DEACTIVATED);
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void reactivatingAnActiveCommunityIsANoOp() {
        Community active = communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(3), Optional.empty(), CommunityStatus.ACTIVE, Optional.empty()));

        reactivate.reactivate(administrator, active.id());

        assertThat(active.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(catalog.calls).isEmpty();
    }
}
