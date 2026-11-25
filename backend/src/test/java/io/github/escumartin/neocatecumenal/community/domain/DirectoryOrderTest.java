package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirectoryOrderTest {

    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    private static Member member(String given, String surnames) {
        return member(new MemberId(UUID.randomUUID()), given, surnames);
    }

    private static Member member(MemberId id, String given, String surnames) {
        return Member.register(id, Community.create(COMMUNITY, new CommunityNumber(1), Optional.empty()),
                PersonName.of(given, surnames), Optional.empty(), Optional.empty(), Instant.EPOCH);
    }

    private static List<String> displayed(List<Member> members) {
        return DirectoryOrder.sorted(members).stream()
                .map(m -> m.name().givenName() + "/" + m.name().surnames().orElse("")).toList();
    }

    @Test
    void ordersBySurnamesThenGivenName() {
        List<Member> members = List.of(member("Luis", "Pérez"), member("Ana", "Zapata"), member("Beatriz", "Pérez"));

        assertThat(displayed(members)).containsExactly("Beatriz/Pérez", "Luis/Pérez", "Ana/Zapata");
    }

    @Test
    void usesTheGivenNameAsKeyWhenThereAreNoSurnames() {
        List<Member> members = List.of(member("Zacarías", "Álvarez"), member("Marta", null), member("Bruno", "Casas"));

        assertThat(displayed(members)).containsExactly("Zacarías/Álvarez", "Bruno/Casas", "Marta/");
    }

    @Test
    void ignoresAccentsAndCase() {
        List<Member> members = List.of(member("Ana", "alvarez"), member("Berta", "Álvarez"), member("Carla", "Alzamora"));

        assertThat(displayed(members)).containsExactly("Ana/alvarez", "Berta/Álvarez", "Carla/Alzamora");
    }

    @Test
    void treatsAnEnyeAsALetterAfterN() {
        List<Member> members = List.of(member("Ana", "Núñez"), member("Berta", "Ñoño"), member("Carla", "Olmo"));

        assertThat(displayed(members)).containsExactly("Ana/Núñez", "Berta/Ñoño", "Carla/Olmo");
    }

    @Test
    void breaksFullTiesWithTheId() {
        MemberId low = new MemberId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        MemberId high = new MemberId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        List<Member> members = new ArrayList<>(List.of(member(high, "Ana", "Pérez"), member(low, "ana", "perez")));

        assertThat(DirectoryOrder.sorted(members)).extracting(Member::id).containsExactly(low, high);
    }

    @Test
    void doesNotModifyTheInputList() {
        List<Member> members = List.of(member("Luis", "Zapata"), member("Ana", "Álvarez"));

        DirectoryOrder.sorted(members);

        assertThat(members).extracting(m -> m.name().givenName()).containsExactly("Luis", "Ana");
    }
}
