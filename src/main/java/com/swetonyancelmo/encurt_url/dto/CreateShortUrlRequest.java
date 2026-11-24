package com.swetonyancelmo.encurt_url.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateShortUrlRequest(
        @Schema(description = "URL original (http ou https)", example = "https://www.example.com/artigo/muito/longo?id=123")
        @NotBlank(message = "A URL é obrigatória")
        @Size(max = 2048, message = "A URL deve ter no máximo 2048 caracteres")
        String url,
        @Schema(description = "Data de expiração (opcional, deve estar no futuro)", example = "2027-12-31T23:59:59Z")
        OffsetDateTime expiresAt
) {
}
