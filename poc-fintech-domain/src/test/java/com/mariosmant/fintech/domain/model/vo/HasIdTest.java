package com.mariosmant.fintech.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every identity Value Object implements {@link HasId} and
 * exposes the wrapped UUID through {@link HasId#value()}.
 *
 * <p>This is a light behavioural test; the stronger structural guarantee
 * (“every {@code *Id} record in {@code domain.model.vo} must implement
 * {@code HasId}”) lives in the ArchUnit fitness-function suite in the
 * boot module.</p>
 */
class HasIdTest {

    @Test
    @DisplayName("AccountId implements HasId<UUID> and returns the wrapped value")
    void accountIdExposesValue() {
        UUID raw = UUID.randomUUID();
        HasId<UUID> id = new AccountId(raw);
        assertThat(id.value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("TransferId implements HasId<UUID>")
    void transferIdExposesValue() {
        UUID raw = UUID.randomUUID();
        HasId<UUID> id = new TransferId(raw);
        assertThat(id.value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("LedgerEntryId implements HasId<UUID>")
    void ledgerEntryIdExposesValue() {
        UUID raw = UUID.randomUUID();
        HasId<UUID> id = new LedgerEntryId(raw);
        assertThat(id.value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("Generic code can treat any identity VO uniformly via HasId")
    void uniformAccessThroughInterface() {
        HasId<UUID>[] ids = new HasId[] {
                AccountId.generate(),
                TransferId.generate(),
                LedgerEntryId.generate()
        };
        for (HasId<UUID> id : ids) {
            assertThat(id.value()).isNotNull();
            assertThat(id.value()).isInstanceOf(UUID.class);
        }
    }
}

