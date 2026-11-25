package com.swetonyancelmo.encurt_url.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swetonyancelmo.encurt_url.model.ShortUrl;

import java.time.OffsetDateTime;

public record ShortUrlResponse(
        @Schema(example = "aB3dE9x") String code,
        @Schema(example = "http://localhost:8080/aB3dE9x") String shortUrl,
        @Schema(example = "https://www.example.com/artigo/muito/longo?id=123") String originalUrl,
        @Schema(example = "2027-12-31T23:59:59Z", nullable = true) OffsetDateTime expiresAt,
        @Schema(example = "true") boolean active,
        @Schema(description = "Token para desativar o link e ver estatísticas (header Authorization: Bearer). "
                + "Aparece somente nesta resposta e não pode ser recuperado depois.",
                example = "q3J0m2v8K1xYw9Zt5bN7cD4eF6gH8iJ0kL2mN4oP6qR")
        String managementToken
) {

    public static ShortUrlResponse from(ShortUrl entity, String shortUrl, String managementToken) {
        return new ShortUrlResponse(
                entity.getCode(),
                shortUrl,
                entity.getOriginalUrl(),
                entity.getExpiresAt(),
                entity.isActive(),
                managementToken);
    }
}
