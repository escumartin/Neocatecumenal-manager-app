package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeMemberContactService;
import io.github.escumartin.neocatecumenal.community.application.service.CreateMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.GetMemberService;
import io.github.escumartin.neocatecumenal.community.application.service.ListMembersService;
import io.github.escumartin.neocatecumenal.community.application.service.RenameMemberService;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
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
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ConsentDecision;
import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.RecorderRole;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class MemberServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CreateMemberService create = new CreateMemberService(communities, members, clock);
    private final RenameMemberService rename = new RenameMemberService(communities, members);
    private final ChangeMemberContactService changeContact =
            new ChangeMemberContactService(communities, members, clock);
    private final GetMemberService get = new GetMemberService(communities, members);
    private final ListMembersService list = new ListMembersService(communities, members);
    private final Community own = community(CommunityStatus.ACTIVE);
    private final Community other = community(CommunityStatus.ACTIVE);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, own.id());
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, own.id());
    private final Actor registered = new Actor(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, own.id());

    private Community community(CommunityStatus status) {
        return communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(communities.stored.size() + 1), Optional.empty(), status, Optional.empty()));
    }

    private Member memberIn(Community community, String given, String surnames) {
        return members.add(Member.register(new MemberId(UUID.randomUUID()), community,
                PersonName.of(given, surnames), Optional.empty(), Optional.empty(), NOW));
    }

    private static ContactChange mobile(String value) {
        return ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share(value)));
    }

    private static OnBehalfConsent verbal() {
        return new OnBehalfConsent(ConsentChannel.VERBAL, true);
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void moderatorCreatesAMemberInTheirOwnCommunityUnderTheCommunityLock() {
        members.log = communities.events;

        MemberView created = create.create(moderator, own.id(), PersonName.of("Ana", "Pérez"), Optional.empty(),
                Optional.empty());

        assertThat(members.stored).containsKey(created.member().id());
        assertThat(created.member().communityId()).isEqualTo(own.id());
        assertThat(created.responsible()).isFalse();
        assertThat(communities.events).containsExactly("lock", "member-save");
    }

    @Test
    void moderatorCannotCreateAMemberInAnotherCommunity() {
        assertThatThrownBy(() -> create.create(moderator, other.id(), PersonName.of("Ana", null), Optional.empty(),
                Optional.empty())).isInstanceOf(CommunityForbidden.class);
        assertThat(members.stored).isEmpty();
    }

    @Test
    void registeredUsersCannotCreateMembers() {
        assertThatThrownBy(() -> create.create(registered, own.id(), PersonName.of("Ana", null), Optional.empty(),
                Optional.empty())).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void administratorChoosesTheCommunity() {
        MemberView created = create.create(administrator, other.id(), PersonName.of("Ana", null), Optional.empty(),
                Optional.empty());

        assertThat(created.member().communityId()).isEqualTo(other.id());
    }

    @Test
    void creatingInAnUnknownCommunityFailsAsNotFound() {
        CommunityId unknown = new CommunityId(UUID.randomUUID());

        assertThatThrownBy(() -> create.create(administrator, unknown, PersonName.of("Ana", null), Optional.empty(),
                Optional.empty())).isInstanceOf(CommunityNotFound.class);
    }

    @Test
    void creatingInADeactivatedCommunityFailsAndStoresNothing() {
        Community deactivated = community(CommunityStatus.DEACTIVATED);

        assertRule(() -> create.create(administrator, deactivated.id(), PersonName.of("Ana", null),
                Optional.empty(), Optional.empty()), "COMMUNITY_DEACTIVATED");
        assertThat(members.stored).isEmpty();
    }

    @Test
    void contactConsentIsRecordedWithTheAuthorAndRoleOfTheModerator() {
        MemberView created = create.create(moderator, own.id(), PersonName.of("Ana", null),
                Optional.of(mobile("600 000 001")), Optional.of(verbal()));

        assertThat(created.member().contact()).containsEntry(ContactField.MOBILE, "600000001");
        ConsentRecord record = created.member().consents().get(0);
        assertThat(record.decision()).isEqualTo(ConsentDecision.GRANTED);
        assertThat(record.recordedBy()).isEqualTo(moderator.id());
        assertThat(record.recorderRole()).isEqualTo(RecorderRole.MODERATOR);
        assertThat(record.channel()).isEqualTo(ConsentChannel.VERBAL);
        assertThat(record.recordedAt()).isEqualTo(NOW);
    }

    @Test
    void contactConsentByAnAdministratorIsRecordedWithTheAdministratorRole() {
        MemberView created = create.create(administrator, own.id(), PersonName.of("Ana", null),
                Optional.of(mobile("600000001")), Optional.of(new OnBehalfConsent(ConsentChannel.WRITTEN, true)));

        assertThat(created.member().consents().get(0).recorderRole()).isEqualTo(RecorderRole.ADMINISTRATOR);
        assertThat(created.member().consents().get(0).channel()).isEqualTo(ConsentChannel.WRITTEN);
    }

    @Test
    void contactWithoutConsentIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> create.create(moderator, own.id(), PersonName.of("Ana", null),
                Optional.of(mobile("600000001")), Optional.empty()))
                .isInstanceOfSatisfying(CommunityFieldInvalid.class,
                        e -> assertThat(e.code()).isEqualTo("CONSENT_REQUIRED"));
        assertThat(members.stored).isEmpty();
    }

    @Test
    void contactWithoutTheNoticeIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> create.create(moderator, own.id(), PersonName.of("Ana", null),
                Optional.of(mobile("600000001")), Optional.of(new OnBehalfConsent(ConsentChannel.VERBAL, false))))
                .isInstanceOfSatisfying(CommunityFieldInvalid.class,
                        e -> assertThat(e.code()).isEqualTo("NOTICE_NOT_GIVEN"));
        assertThat(members.stored).isEmpty();
    }

    @Test
    void moderatorGetsAMemberOfTheirCommunity() {
        Member member = memberIn(own, "Ana", "Pérez");

        MemberView view = get.get(moderator, member.id());

        assertThat(view.member()).isSameAs(member);
        assertThat(view.responsible()).isFalse();
    }

    @Test
    void theViewFlagsTheResponsibleOfTheCommunity() {
        Member member = memberIn(own, "Ana", "Pérez");
        communities.add(Community.restore(own.id(), own.number(), Optional.empty(), CommunityStatus.ACTIVE,
                Optional.of(member.id())));

        assertThat(get.get(moderator, member.id()).responsible()).isTrue();
    }

    @Test
    void aMemberOfAnotherCommunityAnswersNotFoundJustLikeAMissingOne() {
        Member foreign = memberIn(other, "Luis", "Gil");
        MemberId missing = new MemberId(UUID.randomUUID());

        assertThatThrownBy(() -> get.get(moderator, foreign.id())).isInstanceOf(MemberNotFound.class)
                .hasMessage("NOT_FOUND");
        assertThatThrownBy(() -> get.get(moderator, missing)).isInstanceOf(MemberNotFound.class)
                .hasMessage("NOT_FOUND");
    }

    @Test
    void administratorGetsAMemberOfAnyCommunity() {
        Member foreign = memberIn(other, "Luis", "Gil");

        assertThat(get.get(administrator, foreign.id()).member()).isSameAs(foreign);
    }

    @Test
    void listReturnsTheCommunityOrderedByDirectoryOrderAndFlagsTheResponsible() {
        Member zapata = memberIn(own, "Ana", "Zapata");
        Member alvarez = memberIn(own, "Luis", "Álvarez");
        Member noSurnames = memberIn(own, "Marta", null);
        memberIn(other, "Pedro", "Aguirre");
        communities.add(Community.restore(own.id(), own.number(), Optional.empty(), CommunityStatus.ACTIVE,
                Optional.of(noSurnames.id())));

        List<MemberView> listed = list.list(moderator, own.id());

        assertThat(listed).extracting(view -> view.member().id())
                .containsExactly(alvarez.id(), noSurnames.id(), zapata.id());
        assertThat(listed).extracting(MemberView::responsible).containsExactly(false, true, false);
    }

    @Test
    void listOfAnotherCommunityIsForbiddenForAModerator() {
        assertThatThrownBy(() -> list.list(moderator, other.id())).isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void listOfAnUnknownCommunityIsNotFoundForAnAdministrator() {
        assertThatThrownBy(() -> list.list(administrator, new CommunityId(UUID.randomUUID())))
                .isInstanceOf(CommunityNotFound.class);
    }

    @Test
    void renameReplacesTheNameLockingCommunityThenMember() {
        Member member = memberIn(own, "Ana", "Pérez");
        members.log = communities.events;

        MemberView renamed = rename.rename(moderator, member.id(), PersonName.of("Beatriz", null));

        assertThat(renamed.member().name().givenName()).isEqualTo("Beatriz");
        assertThat(members.stored.get(member.id()).name().surnames()).isEmpty();
        assertThat(communities.events).containsExactly("lock", "member-lock", "member-save");
    }

    @Test
    void renameDoesNotConsultTheUserDirectory() {
        assertThat(Arrays.stream(RenameMemberService.class.getConstructors()[0].getParameterTypes()))
                .doesNotContain(UserDirectory.class);
    }

    @Test
    void renameOfAMemberOfAnotherCommunityAnswersNotFound() {
        Member foreign = memberIn(other, "Luis", "Gil");

        assertThatThrownBy(() -> rename.rename(moderator, foreign.id(), PersonName.of("X", null)))
                .isInstanceOf(MemberNotFound.class);
        assertThat(foreign.name().givenName()).isEqualTo("Luis");
    }

    @Test
    void renameByARegisteredUserIsForbidden() {
        Member member = memberIn(own, "Ana", "Pérez");

        assertThatThrownBy(() -> rename.rename(registered, member.id(), PersonName.of("X", null)))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void renameRetriesWhenTheMemberMovedBetweenTheReadAndTheLock() {
        Member member = memberIn(own, "Ana", "Pérez");
        members.lockResult = Optional.of(Member.restore(member.id(), other.id(), member.name(), Map.of(), List.of(),
                Optional.empty()));

        assertThatThrownBy(() -> rename.rename(moderator, member.id(), PersonName.of("X", null)))
                .isInstanceOf(StaleLockOrderException.class);
        assertThat(members.log).doesNotContain("member-save");
    }

    @Test
    void changeContactGrantsConsentWithTheAuthorOfTheModerator() {
        Member member = memberIn(own, "Ana", "Pérez");

        MemberView changed = changeContact.change(moderator, member.id(), mobile("600000001"),
                Optional.of(verbal()));

        assertThat(changed.member().contact()).containsEntry(ContactField.MOBILE, "600000001");
        assertThat(changed.member().consents()).singleElement().satisfies(record -> {
            assertThat(record.recordedBy()).isEqualTo(moderator.id());
            assertThat(record.recorderRole()).isEqualTo(RecorderRole.MODERATOR);
        });
    }

    @Test
    void changeContactWithAnInvalidPhoneStoresNothing() {
        Member member = memberIn(own, "Ana", "Pérez");

        assertThatThrownBy(() -> changeContact.change(moderator, member.id(), mobile("512345678"),
                Optional.of(verbal()))).isInstanceOfSatisfying(CommunityFieldInvalid.class,
                e -> assertThat(e.code()).isEqualTo("PHONE_INVALID"));
        assertThat(members.log).doesNotContain("member-save");
    }

    @Test
    void changeContactOfALinkedMemberCannotAlterWhatIsShared() {
        Member linked = members.add(Member.restore(new MemberId(UUID.randomUUID()), own.id(),
                PersonName.of("Ana", null), Map.of(), List.of(), Optional.of(new UserId(UUID.randomUUID()))));

        assertRule(() -> changeContact.change(moderator, linked.id(), mobile("600000001"), Optional.of(verbal())),
                "CONSENT_MANAGED_BY_MEMBER");
    }

    @Test
    void changeContactMayRectifyTheValueOfAFieldAlreadyShared() {
        Member linked = members.add(Member.restore(new MemberId(UUID.randomUUID()), own.id(),
                PersonName.of("Ana", null), Map.of(ContactField.MOBILE, "600000001"), List.of(),
                Optional.of(new UserId(UUID.randomUUID()))));

        MemberView changed = changeContact.change(moderator, linked.id(), mobile("600000002"), Optional.empty());

        assertThat(changed.member().contact()).containsEntry(ContactField.MOBILE, "600000002");
    }

    @Test
    void changeContactOfAMemberOfAnotherCommunityAnswersNotFound() {
        Member foreign = memberIn(other, "Luis", "Gil");

        assertThatThrownBy(() -> changeContact.change(moderator, foreign.id(), mobile("600000001"),
                Optional.of(verbal()))).isInstanceOf(MemberNotFound.class);
    }

    @Test
    void everyOperationRunsInATransactionAndQueriesAreReadOnly() {
        for (Class<?> service : List.of(CreateMemberService.class, RenameMemberService.class,
                ChangeMemberContactService.class)) {
            assertThat(transactional(service)).isNotNull();
            assertThat(transactional(service).readOnly()).isFalse();
        }
        for (Class<?> service : List.of(GetMemberService.class, ListMembersService.class)) {
            assertThat(transactional(service).readOnly()).isTrue();
        }
    }

    private static Transactional transactional(Class<?> service) {
        Method operation = Arrays.stream(service.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class)).findFirst().orElseThrow();
        return operation.getAnnotation(Transactional.class);
    }
}
