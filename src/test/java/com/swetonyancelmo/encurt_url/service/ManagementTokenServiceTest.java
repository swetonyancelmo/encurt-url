package com.swetonyancelmo.encurt_url.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementTokenServiceTest {

    private final ManagementTokenService service = new ManagementTokenService();

    @Test
    void generatesUrlSafeTokensWith256BitsOfEntropy() {
        for (int i = 0; i < 100; i++) {
            assertThat(service.generate()).matches("[A-Za-z0-9_-]{43}");
        }
    }

    @Test
    void generatesDifferentTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(service.generate());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void hashIsDeterministic64CharHexAndDoesNotContainTheToken() {
        String token = service.generate();

        assertThat(service.hash(token)).hasSize(64).matches("[0-9a-f]{64}")
                .isEqualTo(service.hash(token)).doesNotContain(token);
        assertThat(service.hash(token)).isNotEqualTo(service.hash(service.generate()));
    }

    @Test
    void matchesOnlyTheOriginalToken() {
        String token = service.generate();
        String hash = service.hash(token);

        assertThat(service.matches(token, hash)).isTrue();
        assertThat(service.matches(service.generate(), hash)).isFalse();
        assertThat(service.matches(token + "x", hash)).isFalse();
    }

    @Test
    void neverMatchesWhenTokenOrHashIsMissing() {
        assertThat(service.matches(null, service.hash("x"))).isFalse();
        assertThat(service.matches("x", null)).isFalse(); // link legado, sem hash
    }
}
