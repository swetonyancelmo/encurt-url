package com.swetonyancelmo.encurt_url.controller;

import com.swetonyancelmo.encurt_url.exception.ShortUrlNotFoundException;
import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.service.ClickService;
import com.swetonyancelmo.encurt_url.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    private static final String TARGET = "https://www.example.com/artigo?id=123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @MockitoBean
    private ClickService clickService;

    private ShortUrl shortUrl() {
        return ShortUrl.builder().id(9L).code("aB3dE9x").originalUrl(TARGET).build();
    }

    @Test
    void redirectsWith302AndRegistersClick() throws Exception {
        when(shortUrlService.resolve("aB3dE9x")).thenReturn(shortUrl());

        mockMvc.perform(get("/aB3dE9x")
                        .header("Referer", "https://google.com")
                        .header("User-Agent", "Mozilla/5.0"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", TARGET));

        verify(clickService).registerClick(9L, "https://google.com", "Mozilla/5.0", "127.0.0.1");
    }

    @Test
    void redirectsEvenWithoutOptionalHeaders() throws Exception {
        when(shortUrlService.resolve("aB3dE9x")).thenReturn(shortUrl());

        mockMvc.perform(get("/aB3dE9x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", TARGET));

        verify(clickService).registerClick(9L, null, null, "127.0.0.1");
    }

    @Test
    void returns404AndDoesNotRegisterClickWhenCodeNotFound() throws Exception {
        when(shortUrlService.resolve("nope")).thenThrow(new ShortUrlNotFoundException("nope"));

        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("URL curta não encontrada"));

        verifyNoInteractions(clickService);
    }

    @Test
    void stillRedirectsWhenClickRegistrationFails() throws Exception {
        when(shortUrlService.resolve("aB3dE9x")).thenReturn(shortUrl());
        doThrow(new RuntimeException("banco indisponível"))
                .when(clickService).registerClick(any(), any(), any(), any());

        mockMvc.perform(get("/aB3dE9x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", TARGET));
    }

    @Test
    void codeOutsideAllowedPatternDoesNotReachService() throws Exception {
        mockMvc.perform(get("/abc-def"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(shortUrlService, clickService);
    }
}
