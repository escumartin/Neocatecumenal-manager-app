package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("handler-probes")
@RequestMapping("/probe")
public class ThrowingSpacesEndpoint {

    private Exception failure;

    public void failWith(Exception failure) {
        this.failure = failure;
    }

    @GetMapping
    public String probe() throws Exception {
        throw failure;
    }
}
