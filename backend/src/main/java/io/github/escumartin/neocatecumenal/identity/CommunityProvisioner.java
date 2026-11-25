package io.github.escumartin.neocatecumenal.identity;

import java.util.UUID;

public interface CommunityProvisioner {

    UUID ensure(int number);
}
