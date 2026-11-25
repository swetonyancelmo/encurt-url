package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.exception.InvalidManagementTokenException;
import com.swetonyancelmo.encurt_url.exception.InvalidUrlException;
import com.swetonyancelmo.encurt_url.exception.MissingManagementTokenException;
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
    private ManagementTokenService tokenService = new ManagementTokenService();

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

        CreatedShortUrl created = service.create(URL, expiresAt);
        ShortUrl result = created.shortUrl();

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(persister).insert(captor.capture());
        ShortUrl saved = captor.getValue();
        assertThat(saved.getCode()).matches("[0-9A-Za-z]{7}");
        assertThat(saved.getOriginalUrl()).isEqualTo(URL);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.isActive()).isTrue();
        assertThat(result).isSameAs(saved);
        // só o hash vai para o banco; o token em texto puro volta apenas para quem criou
        assertThat(created.managementToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getManagementTokenHash())
                .isEqualTo(tokenService.hash(created.managementToken()))
                .doesNotContain(created.managementToken());
    }

    @Test
    void createAcceptsNullExpiration() {
        when(persister.insert(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create("http://example.com", null).shortUrl();

        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    void createRetriesWithNewCodeWhenUniqueConstraintIsViolated() {
        when(persister.insert(any(ShortUrl.class)))
                .thenThrow(uniqueViolation(), uniqueViolation())
                .thenAnswer(inv -> inv.getArgument(0));

        CreatedShortUrl created = service.create(URL, null);
        ShortUrl result = created.shortUrl();

        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(persister, times(3)).insert(captor.capture());
        // cada tentativa usa uma entidade nova, sem reaproveitar a que falhou
        assertThat(captor.getAllValues()).doesNotHaveDuplicates();
        assertThat(result).isSameAs(captor.getAllValues().get(2));
        // o token não muda entre tentativas: o devolvido confere com o hash de todas elas
        assertThat(captor.getAllValues()).allSatisfy(c ->
                assertThat(tokenService.matches(created.managementToken(), c.getManagementTokenHash())).isTrue());
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

    private ShortUrl ownedUrl(String token) {
        return ShortUrl.builder().id(1L).code("abc1234").originalUrl(URL)
                .managementTokenHash(tokenService.hash(token)).build();
    }

    @Test
    void deactivateMarksUrlAsInactiveWithTheRightToken() {
        ShortUrl shortUrl = ownedUrl("meu-token");
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(shortUrl));

        service.deactivate("abc1234", "meu-token");

        assertThat(shortUrl.isActive()).isFalse();
    }

    @Test
    void deactivateRequiresAToken() {
        ShortUrl shortUrl = ownedUrl("meu-token");
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> service.deactivate("abc1234", null))
                .isInstanceOf(MissingManagementTokenException.class);
        assertThatThrownBy(() -> service.deactivate("abc1234", "  "))
                .isInstanceOf(MissingManagementTokenException.class);

        assertThat(shortUrl.isActive()).isTrue();
    }

    @Test
    void deactivateRejectsWrongToken() {
        ShortUrl shortUrl = ownedUrl("meu-token");
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> service.deactivate("abc1234", "token-de-outro"))
                .isInstanceOf(InvalidManagementTokenException.class);

        assertThat(shortUrl.isActive()).isTrue();
    }

    @Test
    void legacyUrlWithoutHashCannotBeManagedByAnyToken() {
        ShortUrl legacy = ShortUrl.builder().id(1L).code("abc1234").originalUrl(URL).build();
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.deactivate("abc1234", "qualquer"))
                .isInstanceOf(InvalidManagementTokenException.class);
        assertThat(legacy.isActive()).isTrue();
    }

    @Test
    void deactivateThrowsNotFoundBeforeCheckingTheToken() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate("nope", null))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    void authorizeManagementReturnsTheUrlForTheRightToken() {
        ShortUrl shortUrl = ownedUrl("meu-token");
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(shortUrl));

        assertThat(service.authorizeManagement("abc1234", "meu-token")).isSameAs(shortUrl);
    }

    @Test
    void authorizeManagementRejectsMissingAndWrongTokens() {
        ShortUrl shortUrl = ownedUrl("meu-token");
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> service.authorizeManagement("abc1234", null))
                .isInstanceOf(MissingManagementTokenException.class);
        assertThatThrownBy(() -> service.authorizeManagement("abc1234", "errado"))
                .isInstanceOf(InvalidManagementTokenException.class);
    }

    @Test
    void authorizeManagementThrowsNotFoundForUnknownCode() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorizeManagement("nope", "x"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }
}
