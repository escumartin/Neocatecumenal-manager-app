package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MemberAccessPolicyCommunityTest {

    private static final CommunityId OWN = new CommunityId(UUID.randomUUID());
    private static final CommunityId OTHER = new CommunityId(UUID.randomUUID());

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, OWN);
    }

    @Test
    void administratorManagesCommunities() {
        assertThatCode(() -> MemberAccessPolicy.requireCanManageCommunities(actor(ActorRole.ADMINISTRATOR)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void othersCannotManageCommunities(ActorRole role) {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanManageCommunities(actor(role)))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void administratorDesignatesTheResponsible() {
        assertThatCode(() -> MemberAccessPolicy.requireCanDesignateResponsible(actor(ActorRole.ADMINISTRATOR)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void othersCannotDesignateTheResponsible(ActorRole role) {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanDesignateResponsible(actor(role)))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void administratorViewsAnyCommunityFile() {
        assertThatCode(() -> MemberAccessPolicy.requireCanViewCommunityFile(actor(ActorRole.ADMINISTRATOR), OTHER))
                .doesNotThrowAnyException();
    }

    @Test
    void moderatorViewsTheFileOfTheirOwnCommunity() {
        assertThatCode(() -> MemberAccessPolicy.requireCanViewCommunityFile(actor(ActorRole.MODERATOR), OWN))
                .doesNotThrowAnyException();
    }

    @Test
    void moderatorCannotViewAnotherCommunityFile() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanViewCommunityFile(actor(ActorRole.MODERATOR), OTHER))
                .isInstanceOf(CommunityForbidden.class);
    }

    @Test
    void registeredCannotViewAnyCommunityFile() {
        assertThatThrownBy(() -> MemberAccessPolicy.requireCanViewCommunityFile(actor(ActorRole.REGISTERED), OWN))
                .isInstanceOf(CommunityForbidden.class);
    }
}
