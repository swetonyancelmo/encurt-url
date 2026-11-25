package com.swetonyancelmo.encurt_url.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limites de requisições por IP. Cada regra permite {@code requests} chamadas por {@code window}
 * (com reposição contínua, não em blocos fixos).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, Rule create, Rule management) {

    public record Rule(int requests, Duration window) {

        public Rule {
            if (requests <= 0) {
                throw new IllegalArgumentException("requests deve ser maior que zero");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("window deve ser positiva");
            }
        }
    }
}
