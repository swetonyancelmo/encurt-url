package com.swetonyancelmo.encurt_url.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com a configuração padrão do application.yml: 10 criações e 30 chamadas de gerenciamento por minuto. */
@AutoConfigureMockMvc
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY = "{\"url\":\"https://www.example.com\"}";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String create(String ip) throws Exception {
        return mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(fromIp(ip)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void eleventhCreationInAMinuteIsRejectedAndNothingIsPersisted() throws Exception {
        for (int i = 0; i < 10; i++) {
            create("10.1.0.1");
        }

        mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(fromIp("10.1.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT count(*) FROM short_url", Long.class)).isEqualTo(10);
        // outro IP não é afetado
        create("10.1.0.2");
    }

    @Test
    void redirectIsNeverRateLimited() throws Exception {
        String json = create("10.1.0.3");
        String code = JsonPath.read(json, "$.code");

        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/" + code).with(fromIp("10.1.0.3"))).andExpect(status().isFound());
        }
    }

    @Test
    void wrongTokenGuessesAreCappedAndEvenTheRightTokenIsBlockedMeanwhile() throws Exception {
        String json = create("10.1.0.4");
        String code = JsonPath.read(json, "$.code");
        String token = JsonPath.read(json, "$.managementToken");

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(delete("/api/urls/" + code).header("Authorization", "Bearer chute-" + i)
                            .with(fromIp("10.1.0.4")))
                    .andExpect(status().isForbidden());
        }

        // 31ª tentativa: 429, mesmo com o token certo. O link continua ativo.
        mockMvc.perform(delete("/api/urls/" + code).header("Authorization", "Bearer " + token)
                        .with(fromIp("10.1.0.4")))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        // o dono, de outro IP, continua conseguindo gerenciar
        mockMvc.perform(delete("/api/urls/" + code).header("Authorization", "Bearer " + token)
                        .with(fromIp("10.1.0.5")))
                .andExpect(status().isNoContent());
    }
}
