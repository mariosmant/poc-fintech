package com.mariosmant.fintech.infrastructure.security.reputation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * verifies CIDR parsing, lookup correctness,
 * fail-static refresh semantics, and Spamhaus-DROP-style line tolerance.
 */
class CaffeineIpReputationServiceTest {

    @Test
    @DisplayName("Empty service returns false for any address; size==0")
    void emptyService() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        assertThat(svc.size()).isZero();
        assertThat(svc.isBlocked("8.8.8.8")).isFalse();
    }

    @Test
    @DisplayName("/24 CIDR matches every host in the range; nothing outside")
    void cidrSlash24() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("203.0.113.0/24"));
        assertThat(svc.isBlocked("203.0.113.1")).isTrue();
        assertThat(svc.isBlocked("203.0.113.255")).isTrue();
        assertThat(svc.isBlocked("203.0.112.255")).isFalse();
        assertThat(svc.isBlocked("203.0.114.0")).isFalse();
    }

    @Test
    @DisplayName("Bare IPv4 (no /n) is treated as /32")
    void bareIpIsSlash32() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("198.51.100.42"));
        assertThat(svc.isBlocked("198.51.100.42")).isTrue();
        assertThat(svc.isBlocked("198.51.100.41")).isFalse();
    }

    @Test
    @DisplayName("Spamhaus DROP-style 'cidr ; SBL...' lines are accepted")
    void spamhausLineFormat() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("192.0.2.0/24 ; SBL999999"));
        assertThat(svc.isBlocked("192.0.2.7")).isTrue();
    }

    @Test
    @DisplayName("Comment lines and blanks are skipped")
    void commentsSkipped() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("# Spamhaus DROP", "", "  ", "203.0.113.0/24"));
        assertThat(svc.size()).isEqualTo(1);
        assertThat(svc.isBlocked("203.0.113.5")).isTrue();
    }

    @Test
    @DisplayName("All-malformed refresh keeps previous snapshot (fail-static)")
    void failStaticRefresh() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("203.0.113.0/24"));
        svc.replace(List.of("not-an-ip", "999.999.999.999/40"));
        // Previous snapshot must be preserved.
        assertThat(svc.size()).isEqualTo(1);
        assertThat(svc.isBlocked("203.0.113.1")).isTrue();
    }

    @Test
    @DisplayName("Null / blank / IPv6 input never throws and returns false")
    void hostileInput() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("203.0.113.0/24"));
        assertThat(svc.isBlocked(null)).isFalse();
        assertThat(svc.isBlocked("")).isFalse();
        assertThat(svc.isBlocked("   ")).isFalse();
        assertThat(svc.isBlocked("2001:db8::1")).isFalse(); // IPv6 — not yet supported
        assertThat(svc.isBlocked("garbage")).isFalse();
    }

    @Test
    @DisplayName("/32 prefix matches exactly one host")
    void slash32IsExact() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("198.51.100.10/32"));
        assertThat(svc.isBlocked("198.51.100.10")).isTrue();
        assertThat(svc.isBlocked("198.51.100.11")).isFalse();
    }

    @Test
    @DisplayName("/0 matches every IPv4 (panic-button block-everything entry)")
    void slash0MatchesAll() {
        CaffeineIpReputationService svc = new CaffeineIpReputationService();
        svc.replace(List.of("0.0.0.0/0"));
        assertThat(svc.isBlocked("1.2.3.4")).isTrue();
        assertThat(svc.isBlocked("255.255.255.255")).isTrue();
    }
}

