package com.swetonyancelmo.encurt_url.controller;

import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.service.ClickService;
import com.swetonyancelmo.encurt_url.service.CreatedShortUrl;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Limites pequenos (2 criações e 3 chamadas de gerenciamento por minuto) para exercitar o 429. */
@WebMvcTest(value = ShortUrlController.class, properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.create.requests=2",
        "app.rate-limit.create.window=1m",
        "app.rate-limit.management.requests=3",
        "app.rate-limit.management.window=1m"
})
class RateLimitInterceptorTest {

    private static final String CREATE_BODY = "{\"url\":\"https://example.com\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @MockitoBean
    private ClickService clickService;

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void stubCreate() {
        when(shortUrlService.create(anyString(), any())).thenReturn(new CreatedShortUrl(
                ShortUrl.builder().code("abc1234").originalUrl("https://example.com").build(), "tok"));
    }

    @Test
    void createIsBlockedWith429AfterTheLimit() throws Exception {
        stubCreate();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                            .with(fromIp("10.0.0.1")))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                        .with(fromIp("10.0.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Muitas requisições"))
                .andExpect(jsonPath("$.detail").value(containsString("Tente novamente")));
    }

    @Test
    void retryAfterIsAPositiveNumberOfSeconds() throws Exception {
        stubCreate();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                    .with(fromIp("10.0.0.2")));
        }

        String retryAfter = mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY).with(fromIp("10.0.0.2")))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");

        // 2 requisições por minuto => uma ficha a cada 30s
        org.assertj.core.api.Assertions.assertThat(Long.parseLong(retryAfter)).isBetween(1L, 30L);
    }

    @Test
    void eachIpHasItsOwnQuota() throws Exception {
        stubCreate();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                    .with(fromIp("10.0.0.3")));
        }

        mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                        .with(fromIp("10.0.0.4")))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidRequestsAlsoConsumeTheQuota() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}")
                            .with(fromIp("10.0.0.5")))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}")
                        .with(fromIp("10.0.0.5")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deleteAndStatsShareTheManagementQuota() throws Exception {
        mockMvc.perform(delete("/api/urls/abc1234").with(fromIp("10.0.0.6"))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/urls/abc1234/stats").with(fromIp("10.0.0.6"))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/urls/abc1234").with(fromIp("10.0.0.6"))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/urls/abc1234/stats").with(fromIp("10.0.0.6")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(delete("/api/urls/abc1234").with(fromIp("10.0.0.6")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void createAndManagementQuotasAreSeparate() throws Exception {
        stubCreate();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                    .with(fromIp("10.0.0.7")));
        }

        // a cota de criação esgotou, mas o gerenciamento do mesmo IP segue livre
        mockMvc.perform(delete("/api/urls/abc1234").with(fromIp("10.0.0.7"))).andExpect(status().isNoContent());
    }

    @Test
    void unsupportedMethodsDoNotConsumeTheQuota() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put("/api/urls").with(fromIp("10.0.0.8"))).andExpect(status().isMethodNotAllowed());
        }
        stubCreate();

        mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                        .with(fromIp("10.0.0.8")))
                .andExpect(status().isCreated());
    }

    @Test
    void rotateTokenSharesTheManagementQuota() throws Exception {
        when(shortUrlService.rotateManagementToken(anyString(), any())).thenReturn("novo");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/urls/abc1234/rotate-token").with(fromIp("10.0.0.9")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/urls/abc1234/rotate-token").with(fromIp("10.0.0.9")))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/urls/abc1234/stats").with(fromIp("10.0.0.9")))
                .andExpect(status().isTooManyRequests());
    }
}
