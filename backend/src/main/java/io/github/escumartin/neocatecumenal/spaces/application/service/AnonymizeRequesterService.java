package io.github.escumartin.neocatecumenal.spaces.application.service;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.AnonymizeRequester;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class AnonymizeRequesterService implements AnonymizeRequester {

    private static final Logger log = LoggerFactory.getLogger(AnonymizeRequesterService.class);

    private final BookingRequestRepository requests;

    public AnonymizeRequesterService(BookingRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional
    public void anonymize(UserId userId) {
        int affected = requests.anonymize(userId);
        if (affected > 0) {
            log.atInfo().addKeyValue("userId", userId.value()).addKeyValue("requests", affected).log("Solicitudes de salón anonimizadas");
        }
    }
}
