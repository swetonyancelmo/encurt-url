package com.swetonyancelmo.encurt_url.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {

    /** Mesma versão do docker-compose.yml. O Flyway aplica as migrations reais ao subir o contexto. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @Primary
    ControllableCodeGenerator codeGenerator() {
        return new ControllableCodeGenerator();
    }
}
