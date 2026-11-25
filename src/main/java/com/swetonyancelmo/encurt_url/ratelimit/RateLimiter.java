package com.swetonyancelmo.encurt_url.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Clock;

/**
 * Limitador token bucket, em memória, com um bucket por chave (ex.: IP).
 *
 * <p>Cada bucket começa cheio e repõe fichas de forma contínua. Buckets ociosos por uma janela
 * inteira estariam cheios de qualquer jeito, então expirá-los não altera o resultado e mantém a
 * memória limitada. O estado vale por instância da aplicação: com várias instâncias, o limite
 * efetivo é multiplicado por elas.
 */
public class RateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final long MAX_TRACKED_KEYS = 100_000;

    private final int capacity;
    private final double refillPerMilli;
    private final Clock clock;
    private final Cache<String, Bucket> buckets;

    public RateLimiter(RateLimitProperties.Rule rule, Clock clock) {
        this.capacity = rule.requests();
        this.refillPerMilli = (double) rule.requests() / rule.window().toMillis();
        this.clock = clock;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(rule.window())
                .build();
    }

    public Decision tryAcquire(String key) {
        long now = clock.millis();
        return buckets.get(key, k -> new Bucket(capacity, now)).tryConsume(now);
    }

    public void clear() {
        buckets.invalidateAll();
    }

    private final class Bucket {

        private double tokens;
        private long lastRefill;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        synchronized Decision tryConsume(long now) {
            long elapsed = Math.max(0, now - lastRefill);
            tokens = Math.min(capacity, tokens + elapsed * refillPerMilli);
            lastRefill = now;

            if (tokens >= 1) {
                tokens -= 1;
                return new Decision(true, 0);
            }
            double missing = 1 - tokens;
            long retryAfterSeconds = (long) Math.ceil(missing / refillPerMilli / 1000.0);
            return new Decision(false, Math.max(1, retryAfterSeconds));
        }
    }
}
