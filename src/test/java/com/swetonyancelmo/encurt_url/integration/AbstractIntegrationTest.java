package com.swetonyancelmo.encurt_url.integration;

import com.swetonyancelmo.encurt_url.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base dos testes de integração: Postgres real (Testcontainers), migrations Flyway reais e
 * {@code ddl-auto: validate}, então subir o contexto já valida o mapeamento das entidades.
 * Todos os testes compartilham o mesmo contexto (e o mesmo container).
 */
@SpringBootTest
@Import(IntegrationTestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ControllableCodeGenerator codeGenerator;

    @Autowired
    @Qualifier("createRateLimiter")
    protected RateLimiter createRateLimiter;

    @Autowired
    @Qualifier("managementRateLimiter")
    protected RateLimiter managementRateLimiter;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE short_url RESTART IDENTITY CASCADE");
        codeGenerator.reset();
        codeGenerator.clearThreadState();
        createRateLimiter.clear();
        managementRateLimiter.clear();
    }
}
