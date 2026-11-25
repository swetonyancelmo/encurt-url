package com.swetonyancelmo.encurt_url.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Gera códigos aleatórios base62 de 7 caracteres (cerca de 3,5 trilhões de combinações). */
@Component
public class ShortCodeGenerator {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static final int CODE_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
