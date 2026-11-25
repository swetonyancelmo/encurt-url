package com.swetonyancelmo.encurt_url.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** Relógio controlável, para testar a reposição de fichas sem esperar. */
    static class MutableClock extends Clock {
        private volatile long millis = 1_000_000;

        void advance(Duration d) {
            millis += d.toMillis();
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    private final MutableClock clock = new MutableClock();

    private RateLimiter limiter(int requests, Duration window) {
        return new RateLimiter(new RateLimitProperties.Rule(requests, window), clock);
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter limiter = limiter(3, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isFalse();
    }

    @Test
    void tellsHowLongToWaitUntilTheNextToken() {
        RateLimiter limiter = limiter(2, Duration.ofMinutes(1)); // 1 ficha a cada 30s
        limiter.tryAcquire("ip");
        limiter.tryAcquire("ip");

        assertThat(limiter.tryAcquire("ip").retryAfterSeconds()).isEqualTo(30);

        clock.advance(Duration.ofSeconds(10));
        assertThat(limiter.tryAcquire("ip").retryAfterSeconds()).isEqualTo(20);
    }

    @Test
    void refillsTokensOverTime() {
        RateLimiter limiter = limiter(2, Duration.ofMinutes(1));
        limiter.tryAcquire("ip");
        limiter.tryAcquire("ip");
        assertThat(limiter.tryAcquire("ip").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(30)); // uma ficha de volta
        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isFalse();
    }

    @Test
    void neverRefillsBeyondCapacity() {
        RateLimiter limiter = limiter(2, Duration.ofMinutes(1));
        limiter.tryAcquire("ip");

        clock.advance(Duration.ofHours(1));

        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip").allowed()).isFalse(); // no máximo a capacidade, não 60 fichas
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = limiter(1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2").allowed()).isTrue();
    }

    @Test
    void clearForgetsEverything() {
        RateLimiter limiter = limiter(1, Duration.ofMinutes(1));
        limiter.tryAcquire("ip");
        assertThat(limiter.tryAcquire("ip").allowed()).isFalse();

        limiter.clear();

        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
    }

    @Test
    void concurrentCallsNeverExceedTheLimit() throws Exception {
        int limit = 50;
        RateLimiter limiter = limiter(limit, Duration.ofHours(1)); // sem reposição relevante durante o teste

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return limiter.tryAcquire("ip").allowed();
            }));
        }
        start.countDown();
        long allowed = 0;
        for (Future<Boolean> f : results) {
            if (f.get(10, TimeUnit.SECONDS)) {
                allowed++;
            }
        }
        pool.shutdown();

        assertThat(allowed).isEqualTo(limit);
    }

    @Test
    void ruleRejectsInvalidValues() {
        assertThatThrownBy(() -> new RateLimitProperties.Rule(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Rule(5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
