package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeMembers implements MemberRepository {

    final Map<CommunityId, Long> counts = new HashMap<>();
    final List<CommunityId> queried = new ArrayList<>();
    final Map<MemberId, Member> stored = new LinkedHashMap<>();
    List<String> log = new ArrayList<>();
    Optional<Member> lockResult;

    Member add(Member member) {
        stored.put(member.id(), member);
        return member;
    }

    @Override
    public long countIn(CommunityId communityId) {
        queried.add(communityId);
        return counts.getOrDefault(communityId, 0L);
    }

    @Override
    public Member save(Member member) {
        log.add("member-save");
        stored.put(member.id(), member);
        return member;
    }

    @Override
    public Optional<Member> findById(MemberId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<Member> lock(MemberId id) {
        log.add("member-lock");
        return lockResult != null ? lockResult : findById(id);
    }

    @Override
    public void delete(MemberId id) {
        log.add("member-delete");
        stored.remove(id);
    }

    @Override
    public List<Member> findByCommunity(CommunityId communityId) {
        return stored.values().stream().filter(member -> member.communityId().equals(communityId)).toList();
    }

    @Override
    public Optional<Member> findByLinkedUser(UserId userId) {
        return stored.values().stream().filter(member -> member.linkedUserId().equals(Optional.of(userId)))
                .findFirst();
    }
}
