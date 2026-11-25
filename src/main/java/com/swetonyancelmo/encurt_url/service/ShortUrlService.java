package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.exception.InvalidUrlException;
import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

import java.net.URI;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private static final int MAX_ATTEMPTS = 5;

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlPersister shortUrlPersister;
    private final ShortCodeGenerator codeGenerator;

    /**
     * Propositalmente SEM {@code @Transactional}: cada tentativa de insert roda em sua própria
     * transação (em {@link ShortUrlPersister}). Assim, uma colisão de código marca como
     * rollback-only apenas a transação que falhou, e o retry começa limpo.
     */
    public ShortUrl create(String originalUrl, OffsetDateTime expiresAt) {
        validateUrl(originalUrl);
        if (expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new InvalidUrlException("A data de expiração deve estar no futuro");
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ShortUrl candidate = ShortUrl.builder()
                    .code(codeGenerator.next())
                    .originalUrl(originalUrl)
                    .expiresAt(expiresAt)
                    .build();
            try {
                return shortUrlPersister.insert(candidate);
            } catch (DataIntegrityViolationException e) {
                if (!isUniqueViolation(e)) {
                    throw e;
                }
                log.warn("Colisão de código na tentativa {}/{}", attempt, MAX_ATTEMPTS);
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código único");
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

    /** Só a coluna {@code code} tem UNIQUE além da PK (gerada), então 23505 aqui é colisão de código. */
    private boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && UNIQUE_VIOLATION_SQL_STATE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
