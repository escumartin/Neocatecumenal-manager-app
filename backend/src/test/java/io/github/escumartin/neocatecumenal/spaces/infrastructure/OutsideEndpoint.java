package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import io.github.escumartin.neocatecumenal.spaces.domain.RoomNotFound;
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
        throw new RoomNotFound();
    }
}
