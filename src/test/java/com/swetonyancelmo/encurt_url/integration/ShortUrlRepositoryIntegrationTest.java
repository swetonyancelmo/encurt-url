package com.swetonyancelmo.encurt_url.integration;

import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortUrlRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortUrlRepository repository;

    private ShortUrl save(String code, boolean active, OffsetDateTime expiresAt) {
        return repository.saveAndFlush(ShortUrl.builder()
                .code(code).originalUrl("https://example.com/" + code)
                .active(active).expiresAt(expiresAt).build());
    }

    @Test
    void persistsEntityAndLetsDatabaseFillCreatedAt() {
        // URL > 2048 caracteres: a coluna é TEXT, não VARCHAR
        String longUrl = "https://example.com/" + "a".repeat(3000);
        ShortUrl saved = repository.saveAndFlush(ShortUrl.builder().code("abc1234").originalUrl(longUrl).build());

        ShortUrl found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getOriginalUrl()).isEqualTo(longUrl);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getExpiresAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull().isBeforeOrEqualTo(OffsetDateTime.now().plusMinutes(1));
    }

    @Test
    void findByCodeReturnsTheUrl() {
        save("abc1234", true, null);

        assertThat(repository.findByCode("abc1234")).isPresent();
        assertThat(repository.findByCode("zzzzzzz")).isEmpty();
    }

    @Test
    void findActiveByCodeReturnsActiveUrlWithoutExpiration() {
        save("abc1234", true, null);

        assertThat(repository.findActiveByCode("abc1234", OffsetDateTime.now())).isPresent();
    }

    @Test
    void findActiveByCodeReturnsUrlThatExpiresInTheFuture() {
        save("abc1234", true, OffsetDateTime.now().plusHours(1));

        assertThat(repository.findActiveByCode("abc1234", OffsetDateTime.now())).isPresent();
    }

    @Test
    void findActiveByCodeIgnoresExpiredUrl() {
        save("abc1234", true, OffsetDateTime.now().minusSeconds(1));

        assertThat(repository.findActiveByCode("abc1234", OffsetDateTime.now())).isEmpty();
    }

    @Test
    void findActiveByCodeIgnoresInactiveUrl() {
        save("abc1234", false, null);

        assertThat(repository.findActiveByCode("abc1234", OffsetDateTime.now())).isEmpty();
    }

    @Test
    void duplicateCodeViolatesUniqueConstraintWithSqlState23505() {
        save("abc1234", true, null);

        // Garante a premissa em que o retry do ShortUrlService se apoia (SQLState 23505 na cadeia de causas)
        assertThatThrownBy(() -> save("abc1234", true, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> {
                    Throwable t = e;
                    String state = null;
                    while (t != null && state == null) {
                        if (t instanceof SQLException sql) {
                            state = sql.getSQLState();
                        }
                        t = t.getCause();
                    }
                    assertThat(state).isEqualTo("23505");
                });
    }
}
