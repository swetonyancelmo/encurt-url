package com.swetonyancelmo.encurt_url.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record ClickStatsResponse(
        @Schema(example = "aB3dE9x") String code,
        @Schema(description = "Total de cliques da URL, em todo o histórico", example = "42") long totalClicks,
        @Schema(example = "2026-11-01") LocalDate from,
        @Schema(example = "2026-11-30") LocalDate to,
        List<DailyClick> daily
) {

    public record DailyClick(@Schema(example = "2026-11-24") LocalDate day, @Schema(example = "7") long count) {
    }
}
