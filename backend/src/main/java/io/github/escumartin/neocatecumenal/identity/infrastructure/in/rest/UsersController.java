package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.UsersApi;
import io.github.escumartin.neocatecumenal.generated.model.CreatedUser;
import io.github.escumartin.neocatecumenal.generated.model.InitialPassword;
import io.github.escumartin.neocatecumenal.generated.model.User;
import io.github.escumartin.neocatecumenal.generated.model.UserInput;
import io.github.escumartin.neocatecumenal.generated.model.UserPage;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.DeleteUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.GetUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListUsersQuery;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ResetUserPassword;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUserCommand;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UsersController implements UsersApi {

    private final CreateUser createUser;
    private final UpdateUser updateUser;
    private final DeleteUser deleteUser;
    private final ListUsers listUsers;
    private final GetUser getUser;
    private final ResetUserPassword resetUserPassword;
    private final TransactionRetry retry;

    UsersController(CreateUser createUser, UpdateUser updateUser, DeleteUser deleteUser, ListUsers listUsers,
            GetUser getUser, ResetUserPassword resetUserPassword, TransactionRetry retry) {
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.deleteUser = deleteUser;
        this.listUsers = listUsers;
        this.getUser = getUser;
        this.resetUserPassword = resetUserPassword;
        this.retry = retry;
    }

    @Override
    public ResponseEntity<CreatedUser> createUser(UserInput userInput) {
        CreatedUser created = UsersMapper.toCreatedUser(createUser.create(new CreateUserCommand(
                UsersMapper.displayName(userInput.getName()), UsersMapper.toDomain(userInput.getRole()),
                new CommunityId(userInput.getCommunityId()))));
        return ResponseEntity.created(URI.create("/api/users/" + created.getId())).body(created);
    }

    @Override
    public ResponseEntity<Void> deleteUser(UUID userId) {
        retry.run(() -> deleteUser.delete(new UserId(userId)));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<User> getUser(UUID userId) {
        return ResponseEntity.ok(UsersMapper.toUser(getUser.get(new UserId(userId))));
    }

    @Override
    public ResponseEntity<UserPage> listUsers(UUID communityId, Integer page, Integer size) {
        Optional<CommunityId> community = Optional.ofNullable(communityId).map(CommunityId::new);
        return ResponseEntity.ok(UsersMapper.toPage(listUsers.list(new ListUsersQuery(community, page, size))));
    }

    @Override
    public ResponseEntity<InitialPassword> resetUserPassword(UUID userId) {
        return ResponseEntity.ok(new InitialPassword()
                .initialPassword(resetUserPassword.reset(new UserId(userId)).value()));
    }

    @Override
    public ResponseEntity<User> updateUser(UUID userId, UserInput userInput) {
        UpdateUserCommand command = new UpdateUserCommand(new UserId(userId),
                UsersMapper.displayName(userInput.getName()), UsersMapper.toDomain(userInput.getRole()),
                new CommunityId(userInput.getCommunityId()));
        User updated = UsersMapper.toUser(retry.execute(() -> updateUser.update(command)));
        return ResponseEntity.ok(updated);
    }
}
