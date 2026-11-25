package com.swetonyancelmo.encurt_url.integration;

import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.service.ClickService;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ClickServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortUrlService shortUrlService;
    @Autowired
    private ClickService clickService;

    @Test
    void registerClickStoresEventAndDailyCounterTogether() {
        ShortUrl url = shortUrlService.create("https://example.com", null).shortUrl();

        clickService.registerClick(url.getId(), "https://google.com", "Mozilla/5.0", "203.0.113.10");

        assertThat(clickService.totalClicks(url.getCode())).isEqualTo(1);
        var daily = clickService.dailyStats(url.getCode(),
                LocalDate.now(ZoneOffset.UTC).minusDays(1), LocalDate.now(ZoneOffset.UTC).plusDays(1));
        assertThat(daily).hasSize(1);
        assertThat(daily.getFirst().getCount()).isEqualTo(1);

        String ipHash = jdbc.queryForObject("SELECT ip_hash FROM click_event", String.class);
        assertThat(ipHash).hasSize(64).doesNotContain("203.0.113.10");
    }

    @Test
    void concurrentClicksAreNeverLost() throws Exception {
        ShortUrl url = shortUrlService.create("https://example.com", null).shortUrl();
        int clicks = 50;

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < clicks; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                clickService.registerClick(url.getId(), null, "ua", "198.51.100.7");
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(clickService.totalClicks(url.getCode())).isEqualTo(clicks);
        Long dailyTotal = jdbc.queryForObject("SELECT sum(count) FROM click_daily", Long.class);
        assertThat(dailyTotal).isEqualTo(clicks); // o upsert atômico não perde incrementos
    }
}
