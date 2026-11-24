package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import java.util.List;

public record BootstrapCommand(List<CommunityNumber> communityNumbers, DisplayName administratorName,
        CommunityNumber administratorCommunity) {
}
