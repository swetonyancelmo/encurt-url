package com.swetonyancelmo.encurt_url.integration;

import com.jayway.jsonpath.JsonPath;
import com.swetonyancelmo.encurt_url.exception.InvalidManagementTokenException;
import com.swetonyancelmo.encurt_url.service.CreatedShortUrl;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TokenRotationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShortUrlService service;

    private String hashOf(String code) {
        return jdbc.queryForObject("SELECT management_token_hash FROM short_url WHERE code = ?", String.class, code);
    }

    @Test
    void oldTokenStopsWorkingAndNewOneTakesOver() throws Exception {
        CreatedShortUrl created = service.create("https://example.com", null);
        String code = created.shortUrl().getCode();
        String oldToken = created.managementToken();
        String oldHash = hashOf(code);

        String body = mockMvc.perform(post("/api/urls/" + code + "/rotate-token")
                        .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(body, "$.managementToken");

        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(hashOf(code)).isNotEqualTo(oldHash).hasSize(64).isNotEqualTo(newToken);

        // o antigo morreu em todos os endpoints
        mockMvc.perform(get("/api/urls/" + code + "/stats").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/urls/" + code).header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/urls/" + code + "/rotate-token").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden());

        // o novo funciona, e o link nunca deixou de redirecionar
        mockMvc.perform(get("/api/urls/" + code + "/stats").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());
        mockMvc.perform(delete("/api/urls/" + code).header("Authorization", "Bearer " + newToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void rotationRequiresTheCurrentToken() throws Exception {
        CreatedShortUrl created = service.create("https://example.com", null);
        String code = created.shortUrl().getCode();
        String hashBefore = hashOf(code);

        mockMvc.perform(post("/api/urls/" + code + "/rotate-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/urls/" + code + "/rotate-token").header("Authorization", "Bearer chute"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/urls/naoexiste/rotate-token").header("Authorization", "Bearer x"))
                .andExpect(status().isNotFound());

        assertThat(hashOf(code)).isEqualTo(hashBefore); // nada mudou
        assertThat(service.authorizeManagement(code, created.managementToken())).isNotNull();
    }

    @Test
    void anotherLinksTokenCannotRotate() throws Exception {
        CreatedShortUrl mine = service.create("https://example.com", null);
        CreatedShortUrl other = service.create("https://example.org", null);

        mockMvc.perform(post("/api/urls/" + mine.shortUrl().getCode() + "/rotate-token")
                        .header("Authorization", "Bearer " + other.managementToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rotationWorksOnDeactivatedLinks() throws Exception {
        CreatedShortUrl created = service.create("https://example.com", null);
        String code = created.shortUrl().getCode();
        service.deactivate(code, created.managementToken());

        mockMvc.perform(post("/api/urls/" + code + "/rotate-token")
                        .header("Authorization", "Bearer " + created.managementToken()))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentRotationsWithTheSameTokenHaveExactlyOneWinner() throws Exception {
        CreatedShortUrl created = service.create("https://example.com", null);
        String code = created.shortUrl().getCode();
        int threads = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return service.rotateManagementToken(code, created.managementToken());
                } catch (InvalidManagementTokenException lost) {
                    return null;
                }
            }));
        }
        start.countDown();
        List<String> winners = new ArrayList<>();
        for (Future<String> f : futures) {
            String token = f.get(30, TimeUnit.SECONDS);
            if (token != null) {
                winners.add(token);
            }
        }
        pool.shutdown();

        assertThat(winners).hasSize(1);
        // o único token entregue é o que vale de verdade; o original morreu
        assertThat(service.authorizeManagement(code, winners.getFirst())).isNotNull();
        mockMvc.perform(get("/api/urls/" + code + "/stats")
                        .header("Authorization", "Bearer " + created.managementToken()))
                .andExpect(status().isForbidden());
    }
}
