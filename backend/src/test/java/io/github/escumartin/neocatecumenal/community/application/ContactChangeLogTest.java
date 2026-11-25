package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeMemberContactService;
import io.github.escumartin.neocatecumenal.community.application.service.ChangeOwnContactService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ContactChangeLogTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeMembers members = new FakeMembers();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Community home = communities.add(Community.restore(new CommunityId(UUID.randomUUID()),
            new CommunityNumber(1), Optional.empty(), CommunityStatus.ACTIVE, Optional.empty()));
    private final UserId ownUser = new UserId(UUID.randomUUID());
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, home.id());
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;

    @BeforeEach
    void setUp() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(appender);
        appender.stop();
    }

    private Member member(Optional<UserId> linked) {
        return members.add(Member.restore(new MemberId(UUID.randomUUID()), home.id(), PersonName.of("Ana", "Perez"),
                Map.of(), List.of(), linked));
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(key)).findFirst()
                .map(pair -> pair.value).orElse(null);
    }

    @Test
    void managedContactChangeLogsWhetherConsentWasRecordedAndTheFieldNamesOnly() {
        Member member = member(Optional.empty());
        ContactChange change = ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share("600000001"),
                ContactField.EMAIL, FieldChange.share("ana.test@example.com")));

        new ChangeMemberContactService(communities, members, clock).change(moderator, member.id(), change,
                Optional.of(new OnBehalfConsent(ConsentChannel.VERBAL, true)));

        assertThat(appender.list).filteredOn(event -> event.getFormattedMessage().equals(
                "Contacto de hermano actualizado")).singleElement().satisfies(event -> {
                    assertThat(keyValue(event, "consentRecorded")).isEqualTo(true);
                    assertThat(keyValue(event, "fields")).isEqualTo(List.of("MOBILE", "EMAIL"));
                    assertThat(event.getKeyValuePairs().toString()).doesNotContain("600000001", "example.com");
                });
    }

    @Test
    void managedContactChangeWithoutNewConsentLogsConsentNotRecorded() {
        Member member = member(Optional.empty());

        new ChangeMemberContactService(communities, members, clock).change(moderator, member.id(),
                ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.withdraw())), Optional.empty());

        assertThat(appender.list).filteredOn(event -> event.getFormattedMessage().equals(
                "Contacto de hermano actualizado")).singleElement().satisfies(event -> {
                    assertThat(keyValue(event, "consentRecorded")).isEqualTo(false);
                    assertThat(keyValue(event, "fields")).isEqualTo(List.of("MOBILE"));
                });
    }

    @Test
    void ownContactChangeLogsWhetherConsentWasRecordedAndTheFieldNamesOnly() {
        member(Optional.of(ownUser));
        Actor actor = new Actor(ownUser, ActorRole.REGISTERED, home.id());

        new ChangeOwnContactService(communities, members, users, clock).change(actor,
                ContactChange.of(Map.of(ContactField.LANDLINE, FieldChange.share("600000002"))));

        assertThat(appender.list).filteredOn(event -> event.getFormattedMessage().equals(
                "Datos de contacto propios actualizados")).singleElement().satisfies(event -> {
                    assertThat(keyValue(event, "consentRecorded")).isEqualTo(true);
                    assertThat(keyValue(event, "fields")).isEqualTo(List.of("LANDLINE"));
                    assertThat(event.getKeyValuePairs().toString()).doesNotContain("600000002");
                });
    }
}
