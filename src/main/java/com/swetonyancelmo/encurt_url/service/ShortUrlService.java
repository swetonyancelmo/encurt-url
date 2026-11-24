package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.exception.InvalidUrlException;
import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ShortUrl create(String originalUrl, OffsetDateTime expiresAt) {
        validateUrl(originalUrl);
        if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new InvalidUrlException("A data de expiração deve estar no futuro");
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .code(generateUniqueCode())
                .originalUrl(originalUrl)
                .expiresAt(expiresAt)
                .build();
        return shortUrlRepository.save(shortUrl);
    }

    @Transactional(readOnly = true)
    public ShortUrl resolve(String code) {
        return shortUrlRepository.findActiveByCode(code, OffsetDateTime.now())
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
    }

    @Transactional
    public void deactivate(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        shortUrl.setActive(false);
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("A URL é obrigatória");
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!httpScheme || uri.getHost() == null) {
                throw new InvalidUrlException("A URL deve ser http(s) e conter um host");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("URL malformada: " + url);
        }
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = randomCode();
            if (!shortUrlRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código único");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
