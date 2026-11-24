package com.swetonyancelmo.encurt_url.controller;

import com.swetonyancelmo.encurt_url.dto.ClickStatsResponse;
import com.swetonyancelmo.encurt_url.dto.CreateShortUrlRequest;
import com.swetonyancelmo.encurt_url.dto.ShortUrlResponse;
import com.swetonyancelmo.encurt_url.exception.InvalidPeriodException;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.service.ClickService;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Tag(name = "URLs curtas", description = "Criação, desativação e estatísticas")
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class ShortUrlController {

    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int MAX_PERIOD_DAYS = 366;

    private final ShortUrlService shortUrlService;
    private final ClickService clickService;

    @Operation(summary = "Cria uma URL curta",
            description = "Gera um código único de 7 caracteres. O header Location aponta para a URL curta.")
    @ApiResponse(responseCode = "201", description = "URL curta criada")
    @ApiResponse(responseCode = "400", description = "URL inválida, expiração no passado ou corpo malformado",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        ShortUrl created = shortUrlService.create(request.url(), request.expiresAt());
        URI shortUri = buildShortUri(created.getCode());
        return ResponseEntity.created(shortUri)
                .body(ShortUrlResponse.from(created, shortUri.toString()));
    }

    @Operation(summary = "Desativa uma URL curta",
            description = "Após desativada, o redirecionamento passa a responder 404.")
    @ApiResponse(responseCode = "204", description = "URL desativada")
    @ApiResponse(responseCode = "404", description = "Código não encontrado",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Código da URL curta", example = "aB3dE9x") @PathVariable String code) {
        shortUrlService.deactivate(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Estatísticas de cliques",
            description = "Total histórico e série diária (UTC). Padrão: últimos 30 dias. Período máximo: 366 dias.")
    @ApiResponse(responseCode = "200", description = "Estatísticas retornadas")
    @ApiResponse(responseCode = "400", description = "Período inválido ou data malformada",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Código não encontrado",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{code}/stats")
    public ClickStatsResponse stats(
            @Parameter(description = "Código da URL curta", example = "aB3dE9x") @PathVariable String code,
            @Parameter(description = "Início do período (yyyy-MM-dd)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fim do período (yyyy-MM-dd)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS - 1);

        if (start.isAfter(end)) {
            throw new InvalidPeriodException("'from' não pode ser posterior a 'to'");
        }
        if (start.plusDays(MAX_PERIOD_DAYS).isBefore(end)) {
            throw new InvalidPeriodException("O período máximo é de " + MAX_PERIOD_DAYS + " dias");
        }

        var daily = clickService.dailyStats(code, start, end).stream()
                .map(d -> new ClickStatsResponse.DailyClick(d.getId().getDay(), d.getCount()))
                .toList();

        return new ClickStatsResponse(code, clickService.totalClicks(code), start, end, daily);
    }

    private URI buildShortUri(String code) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .replacePath("/{code}")
                .buildAndExpand(code)
                .toUri();
    }
}
