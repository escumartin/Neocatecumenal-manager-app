package io.github.escumartin.neocatecumenal.agenda.application;

import io.github.escumartin.neocatecumenal.agenda.application.port.out.CommunityLock;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class FakeCommunityLock implements CommunityLock {

    final Set<CommunityId> missing = new HashSet<>();
    final List<CommunityId> locked = new ArrayList<>();
    final List<Boolean> workDoneWhenLocked = new ArrayList<>();
    BooleanSupplier workDone = () -> false;

    @Override
    public boolean lockForShare(CommunityId id) {
        locked.add(id);
        workDoneWhenLocked.add(workDone.getAsBoolean());
        return !missing.contains(id);
    }
}
