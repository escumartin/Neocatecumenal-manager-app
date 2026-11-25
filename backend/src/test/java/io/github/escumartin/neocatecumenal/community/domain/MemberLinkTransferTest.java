package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberLinkTransferTest {

    private static final CommunityId SOURCE_ID = new CommunityId(UUID.randomUUID());
    private static final CommunityId TARGET_ID = new CommunityId(UUID.randomUUID());
    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final PersonName NAME = PersonName.of("Ana", "Perez");

    private static Community community(CommunityId id, CommunityStatus status, Optional<MemberId> responsible) {
        return Community.restore(id, new CommunityNumber(3), Optional.empty(), status, responsible);
    }

    private static Member member(Optional<UserId> linked) {
        return Member.restore(new MemberId(UUID.randomUUID()), SOURCE_ID, NAME, Map.of(), List.of(), linked);
    }

    private static LinkedUser user(CommunityId community) {
        return new LinkedUser(USER, ActorRole.REGISTERED, community, "Different Name");
    }

    private static void assertRule(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityDomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void linkingAUserOfTheSameCommunityRecordsTheLinkWithoutTouchingTheName() {
        Member member = member(Optional.empty());

        member.link(user(SOURCE_ID));

        assertThat(member.linkedUserId()).contains(USER);
        assertThat(member.name()).isEqualTo(NAME);
    }

    @Test
    void linkingAUserOfAnotherCommunityFailsOnTheUserField() {
        Member member = member(Optional.empty());

        assertThatThrownBy(() -> member.link(user(TARGET_ID))).isInstanceOfSatisfying(CommunityFieldInvalid.class,
                e -> {
                    assertThat(e.field()).isEqualTo("userId");
                    assertThat(e.code()).isEqualTo("USER_NOT_IN_COMMUNITY");
                });
        assertThat(member.linkedUserId()).isEmpty();
    }

    @Test
    void linkingAMemberThatAlreadyHasAUserFails() {
        Member member = member(Optional.of(new UserId(UUID.randomUUID())));

        assertRule(() -> member.link(user(SOURCE_ID)), "MEMBER_ALREADY_LINKED");
    }

    @Test
    void unlinkingClearsTheLinkAndKeepsTheName() {
        Member member = member(Optional.of(USER));

        member.unlink();

        assertThat(member.linkedUserId()).isEmpty();
        assertThat(member.name()).isEqualTo(NAME);
    }

    @Test
    void unlinkingAMemberWithoutUserFails() {
        Member member = member(Optional.empty());

        assertRule(member::unlink, "MEMBER_NOT_LINKED");
    }

    @Test
    void transferMovesTheMemberToTheActiveTarget() {
        Member member = member(Optional.of(USER));

        member.transferTo(community(SOURCE_ID, CommunityStatus.ACTIVE, Optional.empty()),
                community(TARGET_ID, CommunityStatus.ACTIVE, Optional.empty()));

        assertThat(member.communityId()).isEqualTo(TARGET_ID);
        assertThat(member.linkedUserId()).contains(USER);
    }

    @Test
    void transferToADeactivatedCommunityFailsAndKeepsTheMemberInPlace() {
        Member member = member(Optional.empty());

        assertRule(() -> member.transferTo(community(SOURCE_ID, CommunityStatus.ACTIVE, Optional.empty()),
                community(TARGET_ID, CommunityStatus.DEACTIVATED, Optional.empty())), "COMMUNITY_DEACTIVATED");
        assertThat(member.communityId()).isEqualTo(SOURCE_ID);
    }

    @Test
    void transferOfTheResponsibleOfTheSourceFails() {
        Member member = member(Optional.empty());
        Community source = community(SOURCE_ID, CommunityStatus.ACTIVE, Optional.of(member.id()));

        assertRule(() -> member.transferTo(source, community(TARGET_ID, CommunityStatus.ACTIVE, Optional.empty())),
                "RESPONSIBLE_REQUIRED");
        assertThat(member.communityId()).isEqualTo(SOURCE_ID);
    }

    @Test
    void transferToTheSameCommunityChangesNothingEvenForTheResponsible() {
        Member member = member(Optional.empty());
        Community source = community(SOURCE_ID, CommunityStatus.ACTIVE, Optional.of(member.id()));

        member.transferTo(source, source);

        assertThat(member.communityId()).isEqualTo(SOURCE_ID);
    }

    @Test
    void transferFromACommunityThatIsNotTheMembersOneIsRejected() {
        Member member = member(Optional.empty());

        assertThatThrownBy(() -> member.transferTo(community(TARGET_ID, CommunityStatus.ACTIVE, Optional.empty()),
                community(TARGET_ID, CommunityStatus.ACTIVE, Optional.empty())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
