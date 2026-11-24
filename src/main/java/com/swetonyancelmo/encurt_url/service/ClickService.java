package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ClickDaily;
import com.swetonyancelmo.encurt_url.model.ClickEvent;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ClickDailyRepository;
import com.swetonyancelmo.encurt_url.repository.ClickEventRepository;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

@Service
public class ClickService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ClickEventRepository clickEventRepository;
    private final ClickDailyRepository clickDailyRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final SecretKeySpec ipHashKey;

    public ClickService(ClickEventRepository clickEventRepository,
                        ClickDailyRepository clickDailyRepository,
                        ShortUrlRepository shortUrlRepository,
                        @Value("${app.ip-hash-secret}") String ipHashSecret) {
        if (ipHashSecret == null || ipHashSecret.isBlank()) {
            throw new IllegalStateException("app.ip-hash-secret não pode ser vazio");
        }
        this.clickEventRepository = clickEventRepository;
        this.clickDailyRepository = clickDailyRepository;
        this.shortUrlRepository = shortUrlRepository;
        this.ipHashKey = new SecretKeySpec(ipHashSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    @Transactional
    public void registerClick(Long shortUrlId, String referrer, String userAgent, String ip) {
        ClickEvent event = ClickEvent.builder()
                .shortUrl(shortUrlRepository.getReferenceById(shortUrlId))
                .referrer(referrer)
                .userAgent(userAgent)
                .ipHash(hashIp(ip))
                .build();
        clickEventRepository.save(event);
        clickDailyRepository.incrementCount(shortUrlId, LocalDate.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public long totalClicks(String code) {
        return clickEventRepository.countByShortUrlId(findByCode(code).getId());
    }

    @Transactional(readOnly = true)
    public List<ClickDaily> dailyStats(String code, LocalDate from, LocalDate to) {
        return clickDailyRepository.findByIdShortUrlIdAndIdDayBetweenOrderByIdDayAsc(
                findByCode(code).getId(), from, to);
    }

    private ShortUrl findByCode(String code) {
        return shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
    }

    private String hashIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(ipHashKey);
            return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular HMAC do IP", e);
        }
    }
}
