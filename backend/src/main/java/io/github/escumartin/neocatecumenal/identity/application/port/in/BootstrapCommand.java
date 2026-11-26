package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import java.util.Objects;

public record BootstrapCommand(DisplayName administratorName, CommunityNumber administratorCommunity) {

    public BootstrapCommand {
        Objects.requireNonNull(administratorName, "administratorName");
        Objects.requireNonNull(administratorCommunity, "administratorCommunity");
    }
}
