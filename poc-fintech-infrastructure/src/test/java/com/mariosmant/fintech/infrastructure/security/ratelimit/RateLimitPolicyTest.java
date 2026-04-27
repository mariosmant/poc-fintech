package com.mariosmant.fintech.infrastructure.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * invariants of the {@link RateLimitPolicy} value type.
 */
class RateLimitPolicyTest {

    @Test
    @DisplayName("fixedWindow factory produces burst == requestsPerMinute")
    void fixedWindow() {
        RateLimitPolicy p = RateLimitPolicy.fixedWindow("default", 100, 60);
        assertThat(p.id()).isEqualTo("default");
        assertThat(p.requestsPerMinute()).isEqualTo(100);
        assertThat(p.burstCapacity()).isEqualTo(100);
        assertThat(p.window().getSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("Rejects blank id, non-positive rpm/window, undersized burst")
    void rejectsInvalid() {
        assertThatThrownBy(() -> new RateLimitPolicy("", 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitPolicy("a", 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitPolicy("a", 1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        // burst < rpm
        assertThatThrownBy(() -> new RateLimitPolicy("a", 10, 60, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("burstCapacity");
    }
}

