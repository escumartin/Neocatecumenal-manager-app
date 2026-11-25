package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MemberRepositoryAdapter implements MemberRepository {

    private static final String USER_CONSTRAINT = "uk_community_member_user";

    private final MemberJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    MemberRepositoryAdapter(MemberJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Member save(Member member) {
        MemberJpaEntity entity = repository.findById(member.id().value()).orElseGet(() -> created(member));
        copy(member, entity);
        try {
            return toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException failure) {
            if (PersistenceViolations.isViolation(failure, PersistenceViolations.UNIQUE_VIOLATION,
                    USER_CONSTRAINT)) {
                throw new CommunityDomainException("USER_ALREADY_LINKED");
            }
            if (PersistenceViolations.isViolation(failure, PersistenceViolations.FOREIGN_KEY_VIOLATION, null)) {
                throw new CommunityNotFound();
            }
            throw failure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findById(MemberId id) {
        return repository.findById(id.value()).map(MemberRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public Optional<Member> lock(MemberId id) {
        return repository.lockRow(id.value()).flatMap(repository::findById).map(entity -> {
            entityManager.refresh(entity);
            return toDomain(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> findByCommunity(CommunityId communityId) {
        return repository.findAllByCommunityId(communityId.value()).stream()
                .map(MemberRepositoryAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countIn(CommunityId communityId) {
        return repository.countByCommunityId(communityId.value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findByLinkedUser(UserId userId) {
        return repository.findByUserId(userId.value()).map(MemberRepositoryAdapter::toDomain);
    }

    private static MemberJpaEntity created(Member member) {
        MemberJpaEntity entity = new MemberJpaEntity();
        entity.id = member.id().value();
        return entity;
    }

    private static void copy(Member member, MemberJpaEntity entity) {
        entity.communityId = member.communityId().value();
        entity.givenName = member.name().givenName();
        entity.surnames = member.name().surnames().orElse(null);
        entity.mobile = member.contact().get(ContactField.MOBILE);
        entity.landline = member.contact().get(ContactField.LANDLINE);
        entity.email = member.contact().get(ContactField.EMAIL);
        entity.userId = member.linkedUserId().map(UserId::value).orElse(null);
        Set<UUID> stored = entity.consents.stream().map(consent -> consent.id).collect(Collectors.toSet());
        member.consents().stream().filter(consent -> !stored.contains(consent.id()))
                .forEach(consent -> entity.consents.add(toEntity(consent)));
    }

    private static ConsentRecordJpaEntity toEntity(ConsentRecord consent) {
        ConsentRecordJpaEntity entity = new ConsentRecordJpaEntity();
        entity.id = consent.id();
        entity.field = consent.field();
        entity.decision = consent.decision();
        entity.channel = consent.channel();
        entity.recordedByUserId = consent.recordedBy().value();
        entity.recorderRole = consent.recorderRole();
        entity.noticeGiven = consent.noticeGiven();
        entity.recordedAt = consent.recordedAt();
        return entity;
    }

    private static Member toDomain(MemberJpaEntity entity) {
        Map<ContactField, String> contact = new EnumMap<>(ContactField.class);
        putIfPresent(contact, ContactField.MOBILE, entity.mobile);
        putIfPresent(contact, ContactField.LANDLINE, entity.landline);
        putIfPresent(contact, ContactField.EMAIL, entity.email);
        List<ConsentRecord> consents = entity.consents.stream().map(MemberRepositoryAdapter::toDomain).toList();
        return Member.restore(new MemberId(entity.id), new CommunityId(entity.communityId),
                PersonName.of(entity.givenName, entity.surnames), contact, consents,
                Optional.ofNullable(entity.userId).map(UserId::new));
    }

    private static void putIfPresent(Map<ContactField, String> contact, ContactField field, String value) {
        if (value != null) {
            contact.put(field, value);
        }
    }

    private static ConsentRecord toDomain(ConsentRecordJpaEntity entity) {
        return new ConsentRecord(entity.id, entity.field, entity.decision, entity.channel,
                new UserId(entity.recordedByUserId), entity.recorderRole, entity.noticeGiven, entity.recordedAt);
    }
}
