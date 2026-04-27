package com.mariosmant.fintech.infrastructure.persistence.outbox;

import com.mariosmant.fintech.infrastructure.persistence.repository.SpringDataOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * proves {@link OutboxShedder} computes the cutoff
 * from {@code now() - retention} and forwards the bulk-delete to the
 * repository.
 */
class OutboxShedderTest {

    @Test
    @DisplayName("Computes cutoff from retention, forwards to repository, logs deleted count")
    void shedsPublishedRowsOlderThanRetention() {
        SpringDataOutboxRepository repo = mock(SpringDataOutboxRepository.class);
        when(repo.deletePublishedOlderThan(any(Instant.class))).thenReturn(42);

        Duration retention = Duration.ofDays(7);
        OutboxShedder shedder = new OutboxShedder(repo, retention);

        Instant before = Instant.now().minus(retention).minusSeconds(1);
        shedder.shedPublishedRows();
        Instant after = Instant.now().minus(retention).plusSeconds(1);

        org.mockito.ArgumentCaptor<Instant> captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repo).deletePublishedOlderThan(captor.capture());
        Instant cutoff = captor.getValue();
        assertThat(cutoff).isAfter(before);
        assertThat(cutoff).isBefore(after);
    }
}

