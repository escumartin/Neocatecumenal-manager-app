package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.InMemoryErasures.Entry;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeMemberContactService;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeOwnContactService;
import io.github.escumartin.neocatecumenal.community.application.service.DeleteMemberService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
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

class ErasureRecordingTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-11-20T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final InMemoryErasures erasures = new InMemoryErasures();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Community home = communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
            new CommunityNumber(1), Optional.empty(), CommunityStatus.ACTIVE, Optional.empty()));
    private final UserId moderatorUser = new UserId(UUID.randomUUID());
    private final Actor moderator = new Actor(moderatorUser, ActorRole.MODERATOR, home.id());
    private final DeleteMemberService delete = new DeleteMemberService(communities, members, users, erasures, clock);
    private final ChangeMemberContactService changeContact =
            new ChangeMemberContactService(communities, members, erasures, clock);
    private final ChangeOwnContactService changeOwnContact =
            new ChangeOwnContactService(communities, members, users, erasures, clock);

    private Member memberWith(Map<ContactField, String> contact, Optional<UserId> linked) {
        List<ConsentRecord> consents = contact.keySet().stream()
                .map(field -> new ConsentRecord(UUID.randomUUID(), field, ConsentDecision.GRANTED,
                        ConsentChannel.VERBAL, moderatorUser, RecorderRole.MODERATOR, true, EARLIER))
                .toList();
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), home.id(), PersonName.of("Ana", "Perez"),
                contact, consents, linked));
    }

    private Optional<OnBehalfConsent> verbalConsent() {
        return Optional.of(new OnBehalfConsent(ConsentChannel.VERBAL, true));
    }

    @Test
    void deletingAMemberRecordsTheWholeMemberWithoutField() {
        Member member = memberWith(Map.of(), Optional.empty());

        delete.delete(moderator, member.id());

        assertThat(erasures.entries).containsExactly(new Entry(member.id(), null, NOW));
    }

    @Test
    void aRejectedMemberDeletionRecordsNothing() {
        Actor outsider = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR,
                new CommunityId(UUID.randomUUID()));
        Member member = memberWith(Map.of(), Optional.empty());

        assertThatThrownBy(() -> delete.delete(outsider, member.id())).isNotNull();

        assertThat(erasures.entries).isEmpty();
    }

    @Test
    void withdrawingAContactFieldRecordsThatFieldForTheMember() {
        Member member = memberWith(Map.of(ContactField.MOBILE, "600000001"), Optional.empty());

        changeContact.change(moderator, member.id(),
                ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.withdraw())), verbalConsent());

        assertThat(erasures.entries).containsExactly(new Entry(member.id(), ContactField.MOBILE, NOW));
    }

    @Test
    void withdrawingTwoFieldsRecordsEachOne() {
        Member member = memberWith(Map.of(ContactField.MOBILE, "600000001", ContactField.EMAIL, "ana@example.com"),
                Optional.empty());

        changeContact.change(moderator, member.id(), ContactChange.of(Map.of(
                ContactField.MOBILE, FieldChange.withdraw(), ContactField.EMAIL, FieldChange.withdraw())),
                verbalConsent());

        assertThat(erasures.entries).extracting(Entry::field)
                .containsExactlyInAnyOrder(ContactField.MOBILE, ContactField.EMAIL);
    }

    @Test
    void withdrawingAFieldThatHadNoValueRecordsNothing() {
        Member member = memberWith(Map.of(), Optional.empty());

        changeContact.change(moderator, member.id(),
                ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.withdraw())), Optional.empty());

        assertThat(erasures.entries).isEmpty();
    }

    @Test
    void sharingANewValueRecordsNothing() {
        Member member = memberWith(Map.of(ContactField.MOBILE, "600000001"), Optional.empty());

        changeContact.change(moderator, member.id(),
                ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share("600000002"))), verbalConsent());

        assertThat(erasures.entries).isEmpty();
    }

    @Test
    void sharingAFirstValueRecordsNothing() {
        Member member = memberWith(Map.of(), Optional.empty());

        changeContact.change(moderator, member.id(),
                ContactChange.of(Map.of(ContactField.EMAIL, FieldChange.share("ana@example.com"))),
                verbalConsent());

        assertThat(erasures.entries).isEmpty();
    }

    @Test
    void aRejectedContactChangeRecordsNothing() {
        Member member = memberWith(Map.of(ContactField.MOBILE, "600000001"), Optional.empty());

        assertThatThrownBy(() -> changeContact.change(moderator, member.id(),
                ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.withdraw())), Optional.empty()))
                .isNotNull();

        assertThat(erasures.entries).isEmpty();
    }

    @Test
    void aMemberWithdrawingTheirOwnFieldRecordsThatField() {
        UserId ownUser = new UserId(UUID.randomUUID());
        Member member = memberWith(Map.of(ContactField.LANDLINE, "900000001"), Optional.of(ownUser));
        Actor actor = new Actor(ownUser, ActorRole.REGISTERED, home.id());

        changeOwnContact.change(actor, ContactChange.of(Map.of(ContactField.LANDLINE, FieldChange.withdraw())));

        assertThat(erasures.entries).containsExactly(new Entry(member.id(), ContactField.LANDLINE, NOW));
    }

    @Test
    void aMemberSharingTheirOwnFieldRecordsNothing() {
        UserId ownUser = new UserId(UUID.randomUUID());
        memberWith(Map.of(), Optional.of(ownUser));
        Actor actor = new Actor(ownUser, ActorRole.REGISTERED, home.id());

        changeOwnContact.change(actor,
                ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share("600000001"))));

        assertThat(erasures.entries).isEmpty();
    }
}
