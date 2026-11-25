package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ClickDaily;
import com.swetonyancelmo.encurt_url.model.ClickDailyId;
import com.swetonyancelmo.encurt_url.model.ClickEvent;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ClickDailyRepository;
import com.swetonyancelmo.encurt_url.repository.ClickEventRepository;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickServiceTest {

    private static final String SECRET = "segredo-de-teste";

    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private ClickDailyRepository clickDailyRepository;
    @Mock
    private ShortUrlRepository shortUrlRepository;

    private ClickService service;

    @BeforeEach
    void setUp() {
        service = new ClickService(clickEventRepository, clickDailyRepository, shortUrlRepository, SECRET);
    }

    @Test
    void registerClickSavesEventAndIncrementsDailyCounter() {
        ShortUrl reference = ShortUrl.builder().id(7L).build();
        when(shortUrlRepository.getReferenceById(7L)).thenReturn(reference);

        LocalDate before = LocalDate.now(ZoneOffset.UTC);
        service.registerClick(7L, "https://google.com", "Mozilla/5.0", "203.0.113.10");
        LocalDate after = LocalDate.now(ZoneOffset.UTC);

        ArgumentCaptor<ClickEvent> eventCaptor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(eventCaptor.capture());
        ClickEvent event = eventCaptor.getValue();
        assertThat(event.getShortUrl()).isSameAs(reference);
        assertThat(event.getReferrer()).isEqualTo("https://google.com");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.getIpHash()).hasSize(64).isEqualTo(hmac("203.0.113.10", SECRET));

        ArgumentCaptor<LocalDate> dayCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(clickDailyRepository).incrementCount(eq(7L), dayCaptor.capture());
        assertThat(dayCaptor.getValue()).isBetween(before, after);
    }

    @Test
    void ipHashIsDeterministicAndDoesNotExposeIp() {
        when(shortUrlRepository.getReferenceById(1L)).thenReturn(ShortUrl.builder().id(1L).build());

        service.registerClick(1L, null, null, "198.51.100.5");
        service.registerClick(1L, null, null, "198.51.100.5");
        service.registerClick(1L, null, null, "198.51.100.6");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository, times(3)).save(captor.capture());
        List<ClickEvent> events = captor.getAllValues();
        assertThat(events.get(0).getIpHash()).isEqualTo(events.get(1).getIpHash());
        assertThat(events.get(0).getIpHash()).isNotEqualTo(events.get(2).getIpHash());
        assertThat(events.get(0).getIpHash()).doesNotContain("198.51.100.5");
    }

    @Test
    void ipHashDependsOnSecret() {
        ClickService other = new ClickService(clickEventRepository, clickDailyRepository,
                shortUrlRepository, "outro-segredo");
        when(shortUrlRepository.getReferenceById(1L)).thenReturn(ShortUrl.builder().id(1L).build());

        service.registerClick(1L, null, null, "198.51.100.5");
        other.registerClick(1L, null, null, "198.51.100.5");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getIpHash())
                .isNotEqualTo(captor.getAllValues().get(1).getIpHash());
    }

    @Test
    void registerClickWithoutIpStoresNullHash() {
        when(shortUrlRepository.getReferenceById(1L)).thenReturn(ShortUrl.builder().id(1L).build());

        service.registerClick(1L, null, null, null);
        service.registerClick(1L, null, null, "  ");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(e -> assertThat(e.getIpHash()).isNull());
    }

    @Test
    void constructorRejectsBlankSecret() {
        assertThatThrownBy(() -> new ClickService(clickEventRepository, clickDailyRepository,
                shortUrlRepository, " "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void totalClicksCountsEventsOfTheUrl() {
        when(shortUrlRepository.findByCode("abc1234")).thenReturn(Optional.of(ShortUrl.builder().id(3L).build()));
        when(clickEventRepository.countByShortUrlId(3L)).thenReturn(42L);

        assertThat(service.totalClicks("abc1234")).isEqualTo(42L);
    }

    @Test
    void totalClicksThrowsWhenCodeNotFound() {
        when(shortUrlRepository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.totalClicks("nope"))
                .isInstanceOf(ShortUrlNotFoundException.class);
        verifyNoInteractions(clickEventRepository);
    }

    @Test
    void dailyStatsReturnsRepositoryResult() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        List<ClickDaily> daily = List.of(ClickDaily.builder()
                .id(new ClickDailyId(3L, from)).count(5).build());
        when(shortUrlRepository.findByCode("abc1234")).thenReturn(Optional.of(ShortUrl.builder().id(3L).build()));
        when(clickDailyRepository.findByIdShortUrlIdAndIdDayBetweenOrderByIdDayAsc(3L, from, to))
                .thenReturn(daily);

        assertThat(service.dailyStats("abc1234", from, to)).isEqualTo(daily);
    }

    @Test
    void dailyStatsThrowsWhenCodeNotFound() {
        when(shortUrlRepository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dailyStats("nope", LocalDate.now(), LocalDate.now()))
                .isInstanceOf(ShortUrlNotFoundException.class);
        verify(clickDailyRepository, never()).findByIdShortUrlIdAndIdDayBetweenOrderByIdDayAsc(any(), any(), any());
    }

    /** Cálculo independente do HMAC esperado, para não depender da implementação. */
    private static String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
