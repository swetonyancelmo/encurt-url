package com.swetonyancelmo.encurt_url.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.Set;

/**
 * Limites aplicados:
 * <ul>
 *   <li>criação de links: {@code POST /api/urls};</li>
 *   <li>gerenciamento (tentativas de token): {@code DELETE /api/urls/{code}} e {@code GET /api/urls/{code}/stats}.</li>
 * </ul>
 * O redirecionamento público ({@code GET /{code}}) não é limitado.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitProperties properties;
    private final RateLimiter createLimiter;
    private final RateLimiter managementLimiter;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
        this.createLimiter = new RateLimiter(properties.create(), Clock.systemUTC());
        this.managementLimiter = new RateLimiter(properties.management(), Clock.systemUTC());
    }

    @Bean
    RateLimiter createRateLimiter() {
        return createLimiter;
    }

    @Bean
    RateLimiter managementRateLimiter() {
        return managementLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!properties.enabled()) {
            return;
        }
        registry.addInterceptor(new RateLimitInterceptor(createLimiter, Set.of("POST")))
                .addPathPatterns("/api/urls");
        registry.addInterceptor(new RateLimitInterceptor(managementLimiter, Set.of("DELETE", "GET")))
                .addPathPatterns("/api/urls/*", "/api/urls/*/stats");
    }
}
