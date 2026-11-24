package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;

public interface ChangeOwnPassword {

    void change(PlainPassword currentPassword, PlainPassword newPassword);
}
