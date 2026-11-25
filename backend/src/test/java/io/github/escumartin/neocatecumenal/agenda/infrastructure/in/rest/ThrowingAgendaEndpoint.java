package io.github.escumartin.neocatecumenal.agenda.infrastructure.in.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("handler-probes")
@RequestMapping("/probe")
public class ThrowingAgendaEndpoint {

    private RuntimeException failure;

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @GetMapping
    public String probe() {
        throw failure;
    }
}
