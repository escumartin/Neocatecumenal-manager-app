package io.github.escumartin.neocatecumenal.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void identifiersRejectNullValues() {
        assertThatThrownBy(() -> new EventId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CommunityId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoomId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BookingRequestId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void identifiersWithTheSameValueAreEqual() {
        UUID value = UUID.randomUUID();

        assertThat(new EventId(value)).isEqualTo(new EventId(value));
    }

    @Test
    void actorRequiresUserRoleAndCommunity() {
        UserId user = new UserId(UUID.randomUUID());
        CommunityId community = new CommunityId(UUID.randomUUID());

        assertThatThrownBy(() -> new Actor(null, ActorRole.MODERATOR, community))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Actor(user, null, community)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Actor(user, ActorRole.MODERATOR, null))
                .isInstanceOf(NullPointerException.class);
    }
}
