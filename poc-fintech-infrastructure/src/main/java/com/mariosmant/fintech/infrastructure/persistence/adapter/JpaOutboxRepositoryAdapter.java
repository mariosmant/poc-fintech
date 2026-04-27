package com.mariosmant.fintech.infrastructure.persistence.adapter;

import com.mariosmant.fintech.application.outbox.OutboxEvent;
import com.mariosmant.fintech.application.port.OutboxRepository;
import com.mariosmant.fintech.infrastructure.persistence.mapper.OutboxEventMapper;
import com.mariosmant.fintech.infrastructure.persistence.repository.SpringDataOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * JPA adapter implementing the {@link OutboxRepository} application port.
 *
 * <p>{@link #findUnpublished(int)} delegates to the native
 * {@code SKIP LOCKED} query so multiple boot instances can poll the same
 * outbox concurrently without double-publishing. The locks held on
 * returned rows are released when the enclosing transaction commits.</p>
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
    public List<OutboxEvent> findUnpublished(int batchSize) {
        // requires a writable transaction so Postgres can take row locks.
        return jpaRepo.findUnpublishedSkipLocked(batchSize)
                .stream().map(OutboxEventMapper::toDomain).toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        jpaRepo.markPublished(eventId);
    }
}

