package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommunityResponsibleTest {

    private static final CommunityId ID = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Community community() {
        return Community.create(ID, new CommunityNumber(12), Optional.empty());
    }

    private static Member memberOf(CommunityId communityId) {
        return Member.restore(new MemberId(UUID.randomUUID()), communityId, PersonName.of("Ana", "Lopez"),
                Map.of(), List.of(), Optional.empty());
    }

    @Test
    void aNewCommunityHasNoResponsible() {
        Community community = community();

        assertThat(community.hasResponsible()).isFalse();
        assertThat(community.responsibleMemberId()).isEmpty();
        assertThat(community.isResponsible(new MemberId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void designatingAMemberOfTheCommunityMakesItTheResponsible() {
        Community community = community();
        Member member = memberOf(ID);

        community.designateResponsible(member);

        assertThat(community.hasResponsible()).isTrue();
        assertThat(community.responsibleMemberId()).contains(member.id());
        assertThat(community.isResponsible(member.id())).isTrue();
    }

    @Test
    void designatingAnotherMemberReplacesTheResponsibleInOneStep() {
        Community community = community();
        Member first = memberOf(ID);
        Member second = memberOf(ID);
        community.designateResponsible(first);

        community.designateResponsible(second);

        assertThat(community.hasResponsible()).isTrue();
        assertThat(community.isResponsible(second.id())).isTrue();
        assertThat(community.isResponsible(first.id())).isFalse();
    }

    @Test
    void designatingTheCurrentResponsibleAgainKeepsIt() {
        Community community = community();
        Member member = memberOf(ID);
        community.designateResponsible(member);

        community.designateResponsible(member);

        assertThat(community.isResponsible(member.id())).isTrue();
    }

    @Test
    void designatingAMemberOfAnotherCommunityIsRejectedAndKeepsTheCurrentResponsible() {
        Community community = community();
        Member own = memberOf(ID);
        community.designateResponsible(own);

        assertThatThrownBy(() -> community.designateResponsible(memberOf(OTHER)))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("MEMBER_NOT_IN_COMMUNITY"));
        assertThat(community.isResponsible(own.id())).isTrue();
    }

    @Test
    void designatingAMemberOfAnotherCommunityOnACommunityWithoutResponsibleLeavesItWithout() {
        Community community = community();

        assertThatThrownBy(() -> community.designateResponsible(memberOf(OTHER)))
                .isInstanceOf(CommunityDomainException.class);
        assertThat(community.hasResponsible()).isFalse();
    }

    @Test
    void theCommunityOffersNoOperationToRemoveTheResponsible() {
        assertThat(Community.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("removeResponsible", "clearResponsible", "dismissResponsible", "undesignateResponsible");
    }
}
