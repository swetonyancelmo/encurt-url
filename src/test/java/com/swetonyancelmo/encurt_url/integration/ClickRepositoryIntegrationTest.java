package com.swetonyancelmo.encurt_url.integration;

import com.swetonyancelmo.encurt_url.model.ClickDailyId;
import com.swetonyancelmo.encurt_url.model.ClickEvent;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ClickDailyRepository;
import com.swetonyancelmo.encurt_url.repository.ClickEventRepository;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);

    @Autowired
    private ShortUrlRepository shortUrlRepository;
    @Autowired
    private ClickEventRepository clickEventRepository;
    @Autowired
    private ClickDailyRepository clickDailyRepository;
    @Autowired
    private TransactionTemplate tx;

    private ShortUrl shortUrl;

    @BeforeEach
    void createUrl() {
        shortUrl = shortUrlRepository.saveAndFlush(
                ShortUrl.builder().code("abc1234").originalUrl("https://example.com").build());
    }

    private void increment(long shortUrlId, LocalDate day) {
        // @Modifying exige uma transação ativa; em produção ela vem do @Transactional do ClickService
        tx.executeWithoutResult(s -> clickDailyRepository.incrementCount(shortUrlId, day));
    }

    private long dailyCount(LocalDate day) {
        return clickDailyRepository.findById(new ClickDailyId(shortUrl.getId(), day)).orElseThrow().getCount();
    }

    @Test
    void incrementCountCreatesRowThenIncrements() {
        increment(shortUrl.getId(), DAY);
        assertThat(dailyCount(DAY)).isEqualTo(1);

        increment(shortUrl.getId(), DAY);
        increment(shortUrl.getId(), DAY);
        assertThat(dailyCount(DAY)).isEqualTo(3);
    }

    @Test
    void incrementCountKeepsDaysSeparate() {
        increment(shortUrl.getId(), DAY);
        increment(shortUrl.getId(), DAY.plusDays(1));
        increment(shortUrl.getId(), DAY.plusDays(1));

        assertThat(dailyCount(DAY)).isEqualTo(1);
        assertThat(dailyCount(DAY.plusDays(1))).isEqualTo(2);
    }

    @Test
    void dailyQueryReturnsRangeInAscendingOrder() {
        increment(shortUrl.getId(), DAY.plusDays(2));
        increment(shortUrl.getId(), DAY);
        increment(shortUrl.getId(), DAY.plusDays(10));

        var result = clickDailyRepository.findByIdShortUrlIdAndIdDayBetweenOrderByIdDayAsc(
                shortUrl.getId(), DAY, DAY.plusDays(5));

        assertThat(result).extracting(d -> d.getId().getDay())
                .containsExactly(DAY, DAY.plusDays(2));
    }

    @Test
    void incrementCountForUnknownUrlViolatesForeignKey() {
        assertThatThrownBy(() -> increment(999_999L, DAY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clickEventIsPersistedWithDatabaseTimestamp() {
        clickEventRepository.saveAndFlush(ClickEvent.builder()
                .shortUrl(shortUrl).referrer("https://google.com")
                .userAgent("Mozilla/5.0").ipHash("a".repeat(64)).build());

        ClickEvent found = clickEventRepository.findAll().getFirst();

        assertThat(found.getOccurredAt()).isNotNull();
        assertThat(found.getReferrer()).isEqualTo("https://google.com");
        assertThat(found.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(found.getIpHash()).hasSize(64);
        assertThat(clickEventRepository.countByShortUrlId(shortUrl.getId())).isEqualTo(1);
    }

    @Test
    void clickEventWithoutOptionalFieldsIsAccepted() {
        clickEventRepository.saveAndFlush(ClickEvent.builder().shortUrl(shortUrl).build());

        assertThat(clickEventRepository.countByShortUrlId(shortUrl.getId())).isEqualTo(1);
    }

    @Test
    void eventsAreFilteredByPeriodNewestFirst() {
        clickEventRepository.saveAndFlush(ClickEvent.builder().shortUrl(shortUrl).referrer("1").build());
        clickEventRepository.saveAndFlush(ClickEvent.builder().shortUrl(shortUrl).referrer("2").build());

        var events = clickEventRepository.findByShortUrlIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                shortUrl.getId(), OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(1));

        assertThat(events).extracting(ClickEvent::getReferrer).containsExactly("2", "1");
    }

    @Test
    void deletingTheUrlCascadesToClicks() {
        increment(shortUrl.getId(), DAY);
        clickEventRepository.saveAndFlush(ClickEvent.builder().shortUrl(shortUrl).build());

        jdbc.update("DELETE FROM short_url WHERE id = ?", shortUrl.getId());

        assertThat(clickEventRepository.count()).isZero();
        assertThat(clickDailyRepository.count()).isZero();
    }
}
