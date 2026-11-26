package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import java.util.ArrayList;
import java.util.List;

final class RecordingCatalog implements IdentityCommunityCatalog {

    final List<String> calls = new ArrayList<>();
    final List<CommunityId> lockedForRemoval = new ArrayList<>();
    RuntimeException failure;

    private void record(String call) {
        if (failure != null) {
            throw failure;
        }
        calls.add(call);
    }

    @Override
    public void register(CommunityId id, CommunityNumber number) {
        record("register:" + id.value() + ":" + number.value());
    }

    @Override
    public void renumber(CommunityId id, CommunityNumber number) {
        record("renumber:" + id.value() + ":" + number.value());
    }

    @Override
    public void setActive(CommunityId id, boolean active) {
        record("setActive:" + id.value() + ":" + active);
    }

    @Override
    public void lockForRemoval(CommunityId id) {
        lockedForRemoval.add(id);
    }

    @Override
    public void remove(CommunityId id) {
        record("remove:" + id.value());
    }
}
