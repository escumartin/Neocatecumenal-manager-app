package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.User;
import java.util.List;

public record UserPage(List<User> items, int page, int size, long totalItems) {
}
