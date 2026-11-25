package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommunityTest {

    private static final CommunityId ID = new CommunityId(UUID.randomUUID());

    private static Community active() {
        return Community.create(ID, new CommunityNumber(12), CommunityName.of("San Pedro"));
    }

    private static Community withResponsible(CommunityStatus status) {
        return Community.restore(ID, new CommunityNumber(12), Optional.empty(), status,
                Optional.of(new MemberId(UUID.randomUUID())));
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void createStartsActiveAndWithoutResponsible() {
        Community community = active();

        assertThat(community.id()).isEqualTo(ID);
        assertThat(community.number()).isEqualTo(new CommunityNumber(12));
        assertThat(community.name()).hasValueSatisfying(name -> assertThat(name.value()).isEqualTo("San Pedro"));
        assertThat(community.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(community.hasResponsible()).isFalse();
    }

    @Test
    void createAcceptsAnAbsentName() {
        assertThat(Community.create(ID, new CommunityNumber(3), Optional.empty()).name()).isEmpty();
    }

    @Test
    void renumberChangesTheNumber() {
        Community community = active();

        community.renumber(new CommunityNumber(40));

        assertThat(community.number()).isEqualTo(new CommunityNumber(40));
    }

    @Test
    void renameChangesAndClearsTheName() {
        Community community = active();

        community.rename(CommunityName.of("Santa Ana"));
        assertThat(community.name()).hasValueSatisfying(name -> assertThat(name.value()).isEqualTo("Santa Ana"));

        community.rename(Optional.empty());
        assertThat(community.name()).isEmpty();
    }

    @Test
    void deactivateWithoutPrivilegedUsersNeedsNoConfirmation() {
        Community community = active();

        community.deactivate(false, false);

        assertThat(community.status()).isEqualTo(CommunityStatus.DEACTIVATED);
    }

    @Test
    void deactivateWithPrivilegedUsersAndNoConfirmationIsRejected() {
        Community community = active();

        assertRule(() -> community.deactivate(true, false), "COMMUNITY_HAS_PRIVILEGED_USERS");
        assertThat(community.status()).isEqualTo(CommunityStatus.ACTIVE);
    }

    @Test
    void deactivateWithPrivilegedUsersAndConfirmationSucceeds() {
        Community community = active();

        community.deactivate(true, true);

        assertThat(community.status()).isEqualTo(CommunityStatus.DEACTIVATED);
    }

    @Test
    void reactivateRestoresTheActiveStatus() {
        Community community = active();
        community.deactivate(false, false);

        community.reactivate();

        assertThat(community.status()).isEqualTo(CommunityStatus.ACTIVE);
    }

    @Test
    void anActiveCommunityAcceptsNewMembers() {
        assertThatCode(() -> active().checkAcceptsNewMembers()).doesNotThrowAnyException();
    }

    @Test
    void aDeactivatedCommunityRejectsNewMembers() {
        Community community = active();
        community.deactivate(false, false);

        assertRule(community::checkAcceptsNewMembers, "COMMUNITY_DEACTIVATED");
    }

    @Test
    void anEmptyCommunityWithoutHistoryIsDeletable() {
        assertThatCode(() -> active().checkDeletable(0, 0, false)).doesNotThrowAnyException();
    }

    @Test
    void aCommunityWithResponsibleIsNotDeletable() {
        assertRule(() -> withResponsible(CommunityStatus.ACTIVE).checkDeletable(0, 0, false),
                "COMMUNITY_HAS_RESPONSIBLE");
    }

    @Test
    void aCommunityWithMembersIsNotDeletable() {
        assertRule(() -> active().checkDeletable(1, 0, false), "COMMUNITY_NOT_EMPTY");
    }

    @Test
    void aCommunityWithUsersIsNotDeletable() {
        assertRule(() -> active().checkDeletable(0, 2, false), "COMMUNITY_NOT_EMPTY");
    }

    @Test
    void aCommunityWithHistoryIsNotDeletable() {
        assertRule(() -> active().checkDeletable(0, 0, true), "COMMUNITY_HAS_HISTORY");
    }

    @Test
    void responsibleTakesPrecedenceOverTheOtherReasons() {
        assertRule(() -> withResponsible(CommunityStatus.ACTIVE).checkDeletable(3, 3, true),
                "COMMUNITY_HAS_RESPONSIBLE");
    }

    @Test
    void emptinessTakesPrecedenceOverHistory() {
        assertRule(() -> active().checkDeletable(1, 0, true), "COMMUNITY_NOT_EMPTY");
    }

    @Test
    void aDeactivatedCommunityWithoutResponsibleIsDeletable() {
        Community community = active();
        community.deactivate(false, false);

        assertThatCode(() -> community.checkDeletable(0, 0, false)).doesNotThrowAnyException();
    }

    @Test
    void aDeactivatedCommunityWithResponsibleIsNotDeletable() {
        assertRule(() -> withResponsible(CommunityStatus.DEACTIVATED).checkDeletable(0, 0, false),
                "COMMUNITY_HAS_RESPONSIBLE");
    }
}
