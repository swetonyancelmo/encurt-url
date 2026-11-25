package com.swetonyancelmo.encurt_url.integration;

import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import com.swetonyancelmo.encurt_url.service.ShortUrlPersister;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortUrlServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "https://www.example.com/artigo";

    @Autowired
    private ShortUrlService service;
    @Autowired
    private ShortUrlPersister persister;
    @Autowired
    private ShortUrlRepository repository;
    @Autowired
    private TransactionTemplate tx;

    private long rowCount() {
        return jdbc.queryForObject("SELECT count(*) FROM short_url", Long.class);
    }

    @Test
    void createPersistsUrlWithIdAndGeneratedCode() {
        ShortUrl created = service.create(URL, null).shortUrl();

        assertThat(created.getId()).isNotNull();
        assertThat(repository.findByCode(created.getCode())).isPresent();
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void createRetriesInANewTransactionAfterRealUniqueViolation() {
        service.create(URL, null);
        jdbc.update("UPDATE short_url SET code = 'AAAAAAA'"); // ocupa o código que vai colidir
        codeGenerator.reset();
        codeGenerator.force("AAAAAAA", "AAAAAAA", "BBBBBBB");

        ShortUrl created = service.create(URL, null).shortUrl();

        assertThat(created.getCode()).isEqualTo("BBBBBBB");
        assertThat(codeGenerator.calls()).isEqualTo(3); // 2 colisões + 1 sucesso
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void createFailsCleanlyWhenEveryAttemptCollides() {
        service.create(URL, null);
        jdbc.update("UPDATE short_url SET code = 'AAAAAAA'");
        codeGenerator.reset();
        codeGenerator.force("AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "AAAAAAA", "BBBBBBB");

        assertThatThrownBy(() -> service.create(URL, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(codeGenerator.calls()).isEqualTo(5);
        assertThat(rowCount()).isEqualTo(1); // nada pela metade
        // e o serviço continua saudável depois da falha
        codeGenerator.reset();
        assertThat(service.create(URL, null).shortUrl().getId()).isNotNull();
    }

    @Test
    void concurrentCreatesThatDrawTheSameCodeAllSucceed() throws Exception {
        int threads = 20;
        codeGenerator.collideOnFirstCallPerThread("COLLIDE");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ShortUrl>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return service.create(URL, null).shortUrl();
            }));
        }
        start.countDown();

        List<String> codes = new ArrayList<>();
        for (Future<ShortUrl> f : futures) {
            codes.add(f.get(30, TimeUnit.SECONDS).getCode()); // lança se alguma requisição "tomou 500"
        }
        pool.shutdown();

        assertThat(codes).doesNotHaveDuplicates().hasSize(threads);
        assertThat(codes).containsOnlyOnce("COLLIDE"); // uma ganhou o código; as outras 19 tentaram de novo
        assertThat(rowCount()).isEqualTo(threads);
        assertThat(codeGenerator.calls()).isEqualTo(threads + (threads - 1));
    }

    /**
     * Documenta o motivo do desenho (retry fora da transação): dentro de UMA transação, depois da
     * violação de unicidade o Postgres aborta o restante dela, então não dá para tentar de novo ali.
     */
    @Test
    void retryInsideTheSameTransactionIsImpossibleAfterUniqueViolation() {
        service.create(URL, null);
        jdbc.update("UPDATE short_url SET code = 'AAAAAAA'");

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            try {
                persister.insert(ShortUrl.builder().code("AAAAAAA").originalUrl(URL).build());
            } catch (RuntimeException collision) {
                // "retry" na mesma transação com outro código:
            }
            persister.insert(ShortUrl.builder().code("BBBBBBB").originalUrl(URL).build());
        })).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM short_url WHERE code = 'BBBBBBB'", Long.class))
                .isZero();
    }

    @Test
    void managementTokenIsStoredOnlyAsHash() {
        var created = service.create(URL, null);

        String stored = jdbc.queryForObject("SELECT management_token_hash FROM short_url WHERE code = ?",
                String.class, created.shortUrl().getCode());

        assertThat(stored).hasSize(64).isNotEqualTo(created.managementToken());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM short_url WHERE management_token_hash = ?",
                Long.class, created.managementToken())).isZero();
    }

    @Test
    void retryAfterCollisionKeepsTheSameTokenValid() {
        service.create(URL, null);
        jdbc.update("UPDATE short_url SET code = 'AAAAAAA'");
        codeGenerator.reset();
        codeGenerator.force("AAAAAAA", "BBBBBBB");

        var created = service.create(URL, null);

        assertThat(created.shortUrl().getCode()).isEqualTo("BBBBBBB");
        assertThat(service.authorizeManagement("BBBBBBB", created.managementToken())).isNotNull();
    }

    @Test
    void resolveAndDeactivateWorkAgainstTheRealDatabase() {
        var createdWithToken = service.create(URL, null);
        ShortUrl created = createdWithToken.shortUrl();

        assertThat(service.resolve(created.getCode()).getOriginalUrl()).isEqualTo(URL);

        service.deactivate(created.getCode(), createdWithToken.managementToken());

        assertThat(repository.findByCode(created.getCode()).orElseThrow().isActive()).isFalse();
        assertThatThrownBy(() -> service.resolve(created.getCode()))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }
}
