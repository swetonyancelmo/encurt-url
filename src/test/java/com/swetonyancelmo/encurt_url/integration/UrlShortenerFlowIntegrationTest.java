package com.swetonyancelmo.encurt_url.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    private String createUrl(String body) throws Exception {
        String response = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.code");
    }

    @Test
    void createRedirectAndReadStatsEndToEnd() throws Exception {
        String code = createUrl("{\"url\":\"" + TARGET + "\"}");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/" + code).header("Referer", "https://google.com"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", TARGET));
        }

        mockMvc.perform(get("/api/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.daily.length()").value(1))
                .andExpect(jsonPath("$.daily[0].count").value(3));
    }

    @Test
    void deactivatedUrlStopsRedirectingButKeepsItsStats() throws Exception {
        String code = createUrl("{\"url\":\"" + TARGET + "\"}");
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        mockMvc.perform(delete("/api/urls/" + code)).andExpect(status().isNoContent());

        mockMvc.perform(get("/" + code))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("URL curta não encontrada"));
        mockMvc.perform(get("/api/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1));
    }

    @Test
    void expiredUrlStopsRedirecting() throws Exception {
        String code = createUrl("{\"url\":\"" + TARGET + "\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        jdbc.update("UPDATE short_url SET expires_at = NOW() - INTERVAL '1 second' WHERE code = ?", code);

        mockMvc.perform(get("/" + code)).andExpect(status().isNotFound());
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
