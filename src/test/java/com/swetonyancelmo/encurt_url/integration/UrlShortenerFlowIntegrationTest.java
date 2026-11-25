package com.swetonyancelmo.encurt_url.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo completo pela API HTTP: controller, service, repository, Flyway e Postgres reais. */
@AutoConfigureMockMvc
class UrlShortenerFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String TARGET = "https://www.example.com/artigo?id=123";

    @Autowired
    private MockMvc mockMvc;

    /** Resposta da criação: código curto e token de gerenciamento. */
    private record Created(String code, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    private Created createUrl(String body) throws Exception {
        var result = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse();
        String json = result.getContentAsString();
        return new Created(JsonPath.read(json, "$.code"), JsonPath.read(json, "$.managementToken"));
    }

    private Created createDefault() throws Exception {
        return createUrl("{\"url\":\"" + TARGET + "\"}");
    }

    @Test
    void createRedirectAndReadStatsEndToEnd() throws Exception {
        Created url = createDefault();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/" + url.code()).header("Referer", "https://google.com"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", TARGET));
        }

        mockMvc.perform(get("/api/urls/" + url.code() + "/stats").header("Authorization", url.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(url.code()))
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.daily.length()").value(1))
                .andExpect(jsonPath("$.daily[0].count").value(3));
    }

    @Test
    void deactivatedUrlStopsRedirectingButKeepsItsStats() throws Exception {
        Created url = createDefault();
        mockMvc.perform(get("/" + url.code())).andExpect(status().isFound());

        mockMvc.perform(delete("/api/urls/" + url.code()).header("Authorization", url.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/" + url.code()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("URL curta não encontrada"));
        mockMvc.perform(get("/api/urls/" + url.code() + "/stats").header("Authorization", url.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1));
    }

    @Test
    void managementEndpointsRejectCallsWithoutToken() throws Exception {
        Created url = createDefault();

        mockMvc.perform(delete("/api/urls/" + url.code()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mockMvc.perform(get("/api/urls/" + url.code() + "/stats"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/" + url.code())).andExpect(status().isFound()); // continua ativo
    }

    @Test
    void managementEndpointsRejectWrongAndForeignTokens() throws Exception {
        Created mine = createDefault();
        Created someoneElses = createDefault();

        mockMvc.perform(delete("/api/urls/" + mine.code()).header("Authorization", "Bearer token-inventado"))
                .andExpect(status().isForbidden());
        // o token de outro link não vale para este
        mockMvc.perform(delete("/api/urls/" + mine.code()).header("Authorization", someoneElses.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/urls/" + mine.code() + "/stats").header("Authorization", someoneElses.bearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/" + mine.code())).andExpect(status().isFound()); // continua ativo
        mockMvc.perform(get("/" + someoneElses.code())).andExpect(status().isFound());
    }

    @Test
    void eachLinkGetsItsOwnToken() throws Exception {
        assertThat(createDefault().token()).isNotEqualTo(createDefault().token());
    }

    @Test
    void tokenIsNeverReturnedAgain() throws Exception {
        Created url = createDefault();

        mockMvc.perform(get("/api/urls/" + url.code() + "/stats").header("Authorization", url.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managementToken").doesNotExist());
    }

    @Test
    void expiredUrlStopsRedirecting() throws Exception {
        Created url = createUrl("{\"url\":\"" + TARGET + "\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
        mockMvc.perform(get("/" + url.code())).andExpect(status().isFound());

        jdbc.update("UPDATE short_url SET expires_at = NOW() - INTERVAL '1 second' WHERE code = ?", url.code());

        mockMvc.perform(get("/" + url.code())).andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestsReturnProblemDetails() throws Exception {
        mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("URL inválida"));

        mockMvc.perform(get("/naoexiste")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/urls/naoexiste/stats")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/urls/naoexiste")).andExpect(status().isNotFound());
    }
}
