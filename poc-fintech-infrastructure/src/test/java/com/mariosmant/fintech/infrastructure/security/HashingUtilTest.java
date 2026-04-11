package com.mariosmant.fintech.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HashingUtil} — SHA3-256 NIST-compliant hashing.
 *
 * @author mariosmant
 * @since 1.0.0
 */
class HashingUtilTest {

    @Test
    @DisplayName("Should produce consistent SHA3-256 hash")
    void shouldProduceConsistentHash() {
        String hash1 = HashingUtil.hash("test-idempotency-key");
        String hash2 = HashingUtil.hash("test-idempotency-key");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Different inputs should produce different hashes")
    void differentInputsDifferentHashes() {
        String hash1 = HashingUtil.hash("key-alpha");
        String hash2 = HashingUtil.hash("key-beta");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Hash should be 64 hex chars (256 bits)")
    void hashLengthShouldBe64() {
        String hash = HashingUtil.hash("any-input");
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("verify() should return true for matching input/hash")
    void verifyShouldMatch() {
        String input = "verify-test";
        String hash = HashingUtil.hash(input);
        assertThat(HashingUtil.verify(input, hash)).isTrue();
    }

    @Test
    @DisplayName("verify() should return false for non-matching")
    void verifyShouldNotMatch() {
        assertThat(HashingUtil.verify("input", "0000000000000000000000000000000000000000000000000000000000000000"))
                .isFalse();
    }
}

