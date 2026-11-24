package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import java.util.List;

public interface ResolvePendingSteps {

    List<PendingStep> ofCurrentSession();
}
