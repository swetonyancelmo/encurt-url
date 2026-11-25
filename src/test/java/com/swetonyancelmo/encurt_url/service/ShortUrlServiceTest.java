package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.exception.InvalidUrlException;
import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final String URL = "https://www.example.com/artigo?id=1";

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private ShortUrlPersister persister;

    @Spy
    private ShortCodeGenerator codeGenerator = new ShortCodeGenerator();

    @InjectMocks
    private ShortUrlService service;

    private static DataIntegrityViolationException uniqueViolation() {
        return new DataIntegrityViolationException("duplicate key",
                new SQLException("duplicate key value violates unique constraint", "23505"));
    }

    @Test
    void createSavesUrlWithGeneratedCode() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);
        when(persister.insert(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create(URL, expiresAt);

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(persister).insert(captor.capture());
        ShortUrl saved = captor.getValue();
        assertThat(saved.getCode()).matches("[0-9A-Za-z]{7}");
        assertThat(saved.getOriginalUrl()).isEqualTo(URL);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.isActive()).isTrue();
        assertThat(result).isSameAs(saved);
    }

    @Test
    void createAcceptsNullExpiration() {
        when(persister.insert(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create("http://example.com", null);

        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    void createRetriesWithNewCodeWhenUniqueConstraintIsViolated() {
        when(persister.insert(any(ShortUrl.class)))
                .thenThrow(uniqueViolation(), uniqueViolation())
                .thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create(URL, null);

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(persister, times(3)).insert(captor.capture());
        // cada tentativa usa uma entidade nova, sem reaproveitar a que falhou
        assertThat(captor.getAllValues()).doesNotHaveDuplicates();
        assertThat(result).isSameAs(captor.getAllValues().get(2));
    }

    @Test
    void createFailsAfterTooManyCollisions() {
        when(persister.insert(any(ShortUrl.class))).thenThrow(uniqueViolation());

        assertThatThrownBy(() -> service.create(URL, null))
                .isInstanceOf(IllegalStateException.class);

        verify(persister, times(5)).insert(any(ShortUrl.class));
    }

    @Test
    void createDoesNotRetryOnOtherIntegrityViolations() {
        DataIntegrityViolationException notNull = new DataIntegrityViolationException("null value",
                new SQLException("null value in column violates not-null constraint", "23502"));
        when(persister.insert(any(ShortUrl.class))).thenThrow(notNull);

        assertThatThrownBy(() -> service.create(URL, null)).isSameAs(notNull);

        verify(persister, times(1)).insert(any(ShortUrl.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "ftp://example.com", "not a url", "http://", "/relativo", "example.com", "javascript:alert(1)"})
    void createRejectsInvalidUrls(String url) {
        assertThatThrownBy(() -> service.create(url, null))
                .isInstanceOf(InvalidUrlException.class);

        verifyNoInteractions(repository, persister);
    }

    @Test
    void createRejectsExpirationInThePast() {
        assertThatThrownBy(() -> service.create(URL, OffsetDateTime.now().minusMinutes(1)))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("futuro");

        verifyNoInteractions(repository, persister);
    }

    @Test
    void resolveReturnsActiveUrl() {
        ShortUrl shortUrl = ShortUrl.builder().id(1L).code("abc1234").originalUrl(URL).build();
        when(repository.findActiveByCode(eq("abc1234"), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(shortUrl));

        assertThat(service.resolve("abc1234")).isSameAs(shortUrl);
    }

    @Test
    void resolveThrowsWhenNotFoundInactiveOrExpired() {
        when(repository.findActiveByCode(eq("nope"), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("nope"))
                .isInstanceOf(ShortUrlNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void deactivateMarksUrlAsInactive() {
        ShortUrl shortUrl = ShortUrl.builder().id(1L).code("abc1234").originalUrl(URL).build();
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(shortUrl));

        service.deactivate("abc1234");

        assertThat(shortUrl.isActive()).isFalse();
    }

    @Test
    void deactivateThrowsWhenNotFound() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate("nope"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }
}
