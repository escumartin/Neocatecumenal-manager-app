package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.model.CreatedUser;
import io.github.escumartin.neocatecumenal.generated.model.Role;
import io.github.escumartin.neocatecumenal.generated.model.UserPage;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreatedUserResult;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;

final class UsersMapper {

    private UsersMapper() {
    }

    static DisplayName displayName(String value) {
        try {
            return new DisplayName(value);
        } catch (DomainException invalid) {
            throw new FieldValidationException("name", invalid.code());
        }
    }

    static UserRole toDomain(Role role) {
        return UserRole.valueOf(role.name());
    }

    static io.github.escumartin.neocatecumenal.generated.model.User toUser(User user) {
        return new io.github.escumartin.neocatecumenal.generated.model.User(user.id().value(), user.accessId().value(), user.name().value(),
                Role.valueOf(user.role().name()), user.communityId().value(), user.passwordChangeRequired());
    }

    static CreatedUser toCreatedUser(CreatedUserResult result) {
        User user = result.user();
        return new CreatedUser(user.id().value(), user.accessId().value(), user.name().value(),
                Role.valueOf(user.role().name()), user.communityId().value(), user.passwordChangeRequired(),
                result.initialPassword().value());
    }

    static UserPage toPage(io.github.escumartin.neocatecumenal.identity.application.port.in.UserPage page) {
        return new UserPage(page.items().stream().map(UsersMapper::toUser).toList(), page.page(), page.size(),
                Math.toIntExact(page.totalItems()));
    }
}
