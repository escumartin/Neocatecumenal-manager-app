package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.AnonymizeRequester;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.infrastructure.in.events.UserDeletedListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

class UserDeletedListenerTest {

    private final List<UserId> anonymized = new ArrayList<>();
    private final UserDeletedListener listener = new UserDeletedListener(anonymized::add);

    @Test
    void anonymizesTheDeletedUser() {
        UUID id = UUID.randomUUID();

        listener.on(new UserDeleted(id));

        assertThat(anonymized).containsExactly(new UserId(id));
    }

    @Test
    void aFailureInTheUseCaseReachesThePublisher() {
        AnonymizeRequester failing = userId -> {
            throw new IllegalStateException("database unavailable");
        };

        assertThatThrownBy(() -> new UserDeletedListener(failing).on(new UserDeleted(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listensSynchronouslyWithAPlainEventListener() throws NoSuchMethodException {
        Method method = UserDeletedListener.class.getMethod("on", UserDeleted.class);

        assertThat(method.isAnnotationPresent(EventListener.class)).isTrue();
        assertThat(method.isAnnotationPresent(Async.class)).isFalse();
    }
}
