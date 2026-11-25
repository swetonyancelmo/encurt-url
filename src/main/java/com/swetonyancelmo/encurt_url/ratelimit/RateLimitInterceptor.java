package com.swetonyancelmo.encurt_url.ratelimit;

import com.swetonyancelmo.encurt_url.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Aplica um {@link RateLimiter} por IP às requisições dos métodos indicados. Roda antes do
 * controller, então requisições inválidas também consomem a cota. A exceção lançada aqui é
 * tratada pelo {@code GlobalExceptionHandler}.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final Set<String> limitedMethods;

    public RateLimitInterceptor(RateLimiter limiter, Set<String> limitedMethods) {
        this.limiter = limiter;
        this.limitedMethods = limitedMethods;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!limitedMethods.contains(request.getMethod())) {
            return true;
        }

        RateLimiter.Decision decision = limiter.tryAcquire(request.getRemoteAddr());
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
        return true;
    }
}
