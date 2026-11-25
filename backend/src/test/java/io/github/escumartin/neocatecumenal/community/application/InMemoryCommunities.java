package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InMemoryCommunities implements CommunityRepository {

    final Map<CommunityId, Community> stored = new LinkedHashMap<>();
    final List<List<CommunityId>> lockCalls = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    RuntimeException failOnSave;

    Community add(Community community) {
        stored.put(community.id(), community);
        return community;
    }

    @Override
    public Community save(Community community) {
        if (failOnSave != null) {
            throw failOnSave;
        }
        events.add("save");
        stored.put(community.id(), community);
        return community;
    }

    @Override
    public Optional<Community> findById(CommunityId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<Community> findByNumber(CommunityNumber number) {
        return stored.values().stream().filter(community -> community.number().equals(number)).findFirst();
    }

    @Override
    public List<Community> findAll() {
        return stored.values().stream().sorted(Comparator.comparingInt(c -> c.number().value())).toList();
    }

    @Override
    public List<Community> lockInIdOrder(CommunityId... ids) {
        lockCalls.add(Arrays.asList(ids));
        events.add("lock");
        return Arrays.stream(ids).map(stored::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public void delete(CommunityId id) {
        events.add("delete");
        stored.remove(id);
    }
}
