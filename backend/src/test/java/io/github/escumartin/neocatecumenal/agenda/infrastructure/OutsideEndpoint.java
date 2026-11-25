package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import io.github.escumartin.neocatecumenal.agenda.domain.EventNotFound;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("handler-probes")
@RequestMapping("/outside")
class OutsideEndpoint {

    @GetMapping
    String probe() {
        throw new EventNotFound();
    }
}
