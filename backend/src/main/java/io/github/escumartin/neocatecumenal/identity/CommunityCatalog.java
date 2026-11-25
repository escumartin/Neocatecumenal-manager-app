package io.github.escumartin.neocatecumenal.identity;

import java.util.UUID;

public interface CommunityCatalog {

    void register(UUID id, int number);

    void renumber(UUID id, int number);

    void setActive(UUID id, boolean active);

    void remove(UUID id);
}
