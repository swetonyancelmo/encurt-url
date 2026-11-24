package com.swetonyancelmo.encurt_url.controller;

import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.service.ClickService;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Redirecionamento")
@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final ShortUrlService shortUrlService;
    private final ClickService clickService;

    @Operation(summary = "Redireciona para a URL original",
            description = "Responde 302 com o header Location e registra o clique.")
    @ApiResponse(responseCode = "302", description = "Redirecionamento para a URL original")
    @ApiResponse(responseCode = "404", description = "Código inexistente, inativo ou expirado",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{code:[0-9A-Za-z]{1,20}}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "Código da URL curta", example = "aB3dE9x") @PathVariable String code,
            @Parameter(hidden = true) HttpServletRequest request) {
        ShortUrl shortUrl = shortUrlService.resolve(code);

        try {
            clickService.registerClick(
                    shortUrl.getId(),
                    request.getHeader(HttpHeaders.REFERER),
                    request.getHeader(HttpHeaders.USER_AGENT),
                    request.getRemoteAddr());
        } catch (RuntimeException e) {
            log.warn("Falha ao registrar clique da URL {}", code, e);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(shortUrl.getOriginalUrl()))
                .build();
    }
}
