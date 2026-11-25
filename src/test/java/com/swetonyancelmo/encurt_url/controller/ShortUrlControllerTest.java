package com.swetonyancelmo.encurt_url.controller;

import com.swetonyancelmo.encurt_url.exception.InvalidManagementTokenException;
import com.swetonyancelmo.encurt_url.exception.InvalidUrlException;
import com.swetonyancelmo.encurt_url.exception.MissingManagementTokenException;
import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ClickDaily;
import com.swetonyancelmo.encurt_url.model.ClickDailyId;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.service.ClickService;
import com.swetonyancelmo.encurt_url.service.CreatedShortUrl;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ShortUrlController.class, properties = "app.rate-limit.enabled=false")
class ShortUrlControllerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @MockitoBean
    private ClickService clickService;

    // ---------- POST /api/urls ----------

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        ShortUrl created = ShortUrl.builder().id(1L).code("aB3dE9x")
                .originalUrl("https://www.example.com").build();
        when(shortUrlService.create(eq("https://www.example.com"), any()))
                .thenReturn(new CreatedShortUrl(created, "token-secreto"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/aB3dE9x"))
                .andExpect(jsonPath("$.code").value("aB3dE9x"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost/aB3dE9x"))
                .andExpect(jsonPath("$.originalUrl").value("https://www.example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.managementToken").value("token-secreto"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void createPassesExpirationToService() throws Exception {
        when(shortUrlService.create(anyString(), any())).thenReturn(new CreatedShortUrl(
                ShortUrl.builder().code("abc1234").originalUrl("https://example.com").build(), "tok"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2027-12-31T23:59:59Z\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(shortUrlService).create(eq("https://example.com"), captor.capture());
        assertThat(captor.getValue().toInstant())
                .isEqualTo(OffsetDateTime.of(2027, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    void createReturns400WhenUrlIsBlank() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.url").exists());

        verifyNoInteractions(shortUrlService);
    }

    @Test
    void createReturns400WhenUrlIsTooLong() throws Exception {
        String longUrl = "https://example.com/" + "a".repeat(2100);

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + longUrl + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());

        verifyNoInteractions(shortUrlService);
    }

    @Test
    void createReturns400WhenBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{url:"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void createReturns400WhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenServiceRejectsUrl() throws Exception {
        when(shortUrlService.create(anyString(), any()))
                .thenThrow(new InvalidUrlException("A URL deve ser http(s) e conter um host"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("URL inválida"))
                .andExpect(jsonPath("$.detail").value("A URL deve ser http(s) e conter um host"));
    }

    @Test
    void createReturns500WithoutLeakingInternalMessage() throws Exception {
        when(shortUrlService.create(anyString(), any()))
                .thenThrow(new RuntimeException("senha do banco: 123"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("senha"))));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(put("/api/urls"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------- DELETE /api/urls/{code} ----------

    @Test
    void deactivateReturns204AndPassesTheBearerToken() throws Exception {
        mockMvc.perform(delete("/api/urls/aB3dE9x").header("Authorization", "Bearer meu-token"))
                .andExpect(status().isNoContent());

        verify(shortUrlService).deactivate("aB3dE9x", "meu-token");
    }

    @Test
    void bearerSchemeIsCaseInsensitive() throws Exception {
        mockMvc.perform(delete("/api/urls/aB3dE9x").header("Authorization", "bearer meu-token"))
                .andExpect(status().isNoContent());

        verify(shortUrlService).deactivate("aB3dE9x", "meu-token");
    }

    @Test
    void deactivateReturns401WithChallengeWhenTokenIsMissing() throws Exception {
        doThrow(new MissingManagementTokenException()).when(shortUrlService).deactivate("aB3dE9x", null);

        mockMvc.perform(delete("/api/urls/aB3dE9x"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Token ausente"));
    }

    @Test
    void otherAuthorizationSchemesAreTreatedAsMissingToken() throws Exception {
        doThrow(new MissingManagementTokenException()).when(shortUrlService).deactivate("aB3dE9x", null);

        mockMvc.perform(delete("/api/urls/aB3dE9x").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateReturns403WhenTokenDoesNotMatch() throws Exception {
        doThrow(new InvalidManagementTokenException()).when(shortUrlService).deactivate("aB3dE9x", "errado");

        mockMvc.perform(delete("/api/urls/aB3dE9x").header("Authorization", "Bearer errado"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void deactivateReturns404WhenCodeNotFound() throws Exception {
        doThrow(new ShortUrlNotFoundException("nope")).when(shortUrlService).deactivate("nope", "tok");

        mockMvc.perform(delete("/api/urls/nope").header("Authorization", "Bearer tok"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("URL curta não encontrada"));
    }

    // ---------- GET /api/urls/{code}/stats ----------

    @Test
    void statsReturnsTotalAndDailySeries() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 24);
        when(clickService.totalClicks("aB3dE9x")).thenReturn(42L);
        when(clickService.dailyStats(eq("aB3dE9x"), any(), any())).thenReturn(List.of(
                ClickDaily.builder().id(new ClickDailyId(1L, day)).count(7).build()));

        mockMvc.perform(get("/api/urls/aB3dE9x/stats").header("Authorization", "Bearer meu-token")
                        .param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("aB3dE9x"))
                .andExpect(jsonPath("$.totalClicks").value(42))
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.daily.length()").value(1))
                .andExpect(jsonPath("$.daily[0].day").value("2026-11-24"))
                .andExpect(jsonPath("$.daily[0].count").value(7));

        verify(shortUrlService).authorizeManagement("aB3dE9x", "meu-token");
        verify(clickService).dailyStats("aB3dE9x", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    }

    @Test
    void statsReturns401WithoutTokenAndNeverTouchesTheData() throws Exception {
        doThrow(new MissingManagementTokenException()).when(shortUrlService).authorizeManagement("aB3dE9x", null);

        mockMvc.perform(get("/api/urls/aB3dE9x/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));

        verifyNoInteractions(clickService);
    }

    @Test
    void statsReturns403WithWrongTokenAndNeverTouchesTheData() throws Exception {
        doThrow(new InvalidManagementTokenException()).when(shortUrlService).authorizeManagement("aB3dE9x", "errado");

        mockMvc.perform(get("/api/urls/aB3dE9x/stats").header("Authorization", "Bearer errado"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clickService);
    }

    @Test
    void statsAuthorizesBeforeValidatingThePeriod() throws Exception {
        doThrow(new InvalidManagementTokenException()).when(shortUrlService).authorizeManagement("aB3dE9x", "errado");

        // período inválido + token errado: quem não é dono recebe 403, não detalhes de validação
        mockMvc.perform(get("/api/urls/aB3dE9x/stats").header("Authorization", "Bearer errado")
                        .param("from", "2026-12-01").param("to", "2026-11-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statsDefaultsToLast30DaysInUtc() throws Exception {
        when(clickService.dailyStats(anyString(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/urls/aB3dE9x/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily").isEmpty());

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(clickService).dailyStats(eq("aB3dE9x"), from.capture(), to.capture());
        assertThat(ChronoUnit.DAYS.between(from.getValue(), to.getValue())).isEqualTo(29);
        assertThat(to.getValue()).isBetween(
                LocalDate.now(ZoneOffset.UTC).minusDays(1), LocalDate.now(ZoneOffset.UTC).plusDays(1));
    }

    @Test
    void statsReturns400WhenFromIsAfterTo() throws Exception {
        mockMvc.perform(get("/api/urls/aB3dE9x/stats")
                        .param("from", "2026-12-01").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Período inválido"));

        verifyNoInteractions(clickService);
    }

    @Test
    void statsReturns400WhenPeriodExceedsMaximum() throws Exception {
        mockMvc.perform(get("/api/urls/aB3dE9x/stats")
                        .param("from", "2024-01-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Período inválido"));
    }

    @Test
    void statsReturns400WhenDateIsMalformed() throws Exception {
        mockMvc.perform(get("/api/urls/aB3dE9x/stats").param("from", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void statsReturns404WhenCodeNotFound() throws Exception {
        when(shortUrlService.authorizeManagement(eq("nope"), any()))
                .thenThrow(new ShortUrlNotFoundException("nope"));

        mockMvc.perform(get("/api/urls/nope/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("URL curta não encontrada"));
    }
}
