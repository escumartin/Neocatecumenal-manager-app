package io.github.escumartin.neocatecumenal.identity.application.port.in;

import java.util.Optional;

public interface GetMyProfile {

    Optional<MyProfile> get();
}
