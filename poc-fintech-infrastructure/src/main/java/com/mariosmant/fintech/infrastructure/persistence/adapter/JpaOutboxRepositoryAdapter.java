package com.mariosmant.fintech.infrastructure.persistence.adapter;

import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.infrastructure.persistence.mapper.OutboxEventMapper;
import com.mariosmant.fintech.infrastructure.persistence.repository.SpringDataOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * JPA adapter implementing the {@link OutboxRepository} application port.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Component
@Transactional
public class JpaOutboxRepositoryAdapter implements OutboxRepository {

    private final SpringDataOutboxRepository jpaRepo;

    public JpaOutboxRepositoryAdapter(SpringDataOutboxRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        var entity = OutboxEventMapper.toEntity(event);
        var saved = jpaRepo.save(entity);
        return OutboxEventMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findUnpublished(int batchSize) {
        return jpaRepo.findUnpublished(PageRequest.ofSize(batchSize))
                .stream().map(OutboxEventMapper::toDomain).toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        jpaRepo.markPublished(eventId);
    }
}

