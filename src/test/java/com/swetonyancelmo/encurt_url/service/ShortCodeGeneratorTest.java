package com.swetonyancelmo.encurt_url.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generatesSevenAlphanumericCharacters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.next()).matches("[0-9A-Za-z]{7}");
        }
    }

    @Test
    void generatesDifferentCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSize(1000);
    }
}
