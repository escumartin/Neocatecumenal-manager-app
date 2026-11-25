package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberContactPropertyTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());
    private static final UserId MODERATOR = new UserId(UUID.randomUUID());
    private static final ConsentContext VERBAL =
            new ConsentContext(MODERATOR, RecorderRole.MODERATOR, ConsentChannel.VERBAL, true);
    private static final Map<ContactField, List<String>> VALUES = Map.of(
            ContactField.MOBILE, List.of("600000001", "600 000 002", "+351912345678", "512345678", "", "abc"),
            ContactField.LANDLINE, List.of("912345678", "912-345-679", "+34912345680", "123", ""),
            ContactField.EMAIL, List.of("ana@example.org", "luis@example.es", "a@b", "", "no-at-sign"));

    private static FieldChange randomChange(Random random, ContactField field) {
        if (random.nextInt(3) == 0) {
            return FieldChange.withdraw();
        }
        List<String> options = VALUES.get(field);
        return FieldChange.share(options.get(random.nextInt(options.size())));
    }

    private static ConsentDecision lastDecision(Member member, ContactField field) {
        ConsentDecision last = null;
        for (ConsentRecord record : member.consents()) {
            if (record.field() == field) {
                last = record.decision();
            }
        }
        return last;
    }

    @Test
    void aValueExistsOnlyWhileTheLastDecisionOfItsFieldIsGranted() {
        for (long seed = 0; seed < 300; seed++) {
            Random random = new Random(seed);
            Member member = Member.register(new MemberId(UUID.randomUUID()),
                    Community.create(COMMUNITY, new CommunityNumber(1), Optional.empty()),
                    PersonName.of("Ana", null), Optional.empty(), Optional.empty(), NOW);
            for (int step = 0; step < 25; step++) {
                Map<ContactField, FieldChange> entries = new EnumMap<>(ContactField.class);
                for (ContactField field : ContactField.values()) {
                    if (random.nextBoolean()) {
                        entries.put(field, randomChange(random, field));
                    }
                }
                try {
                    member.changeContact(ContactChange.of(entries), Optional.of(VERBAL), NOW);
                } catch (CommunityFieldInvalid rejected) {
                    assertThat(rejected.code()).isIn("REQUIRED", "PHONE_INVALID", "EMAIL_INVALID");
                }
                for (ContactField field : ContactField.values()) {
                    boolean hasValue = member.contact().containsKey(field);
                    assertThat(hasValue).as("seed %d step %d %s", seed, step, field)
                            .isEqualTo(lastDecision(member, field) == ConsentDecision.GRANTED);
                }
            }
        }
    }

    @Test
    void consentRecordsHaveNoComponentThatCouldHoldAValue() {
        for (RecordComponent component : ConsentRecord.class.getRecordComponents()) {
            assertThat(component.getType()).isIn(UUID.class, ContactField.class, ConsentDecision.class,
                    ConsentChannel.class, UserId.class, RecorderRole.class, boolean.class, Instant.class);
        }
    }

    @Test
    void noRecordEverContainsAGeneratedValue() {
        Member member = Member.register(new MemberId(UUID.randomUUID()),
                Community.create(COMMUNITY, new CommunityNumber(1), Optional.empty()), PersonName.of("Ana", null),
                Optional.empty(), Optional.empty(), NOW);
        member.changeContact(ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share("600000001"),
                ContactField.EMAIL, FieldChange.share("ana@example.org"))), Optional.of(VERBAL), NOW);
        member.changeContact(ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.withdraw())),
                Optional.of(VERBAL), NOW);

        assertThat(member.consents().toString()).doesNotContain("600000001").doesNotContain("ana@example.org");
    }
}
