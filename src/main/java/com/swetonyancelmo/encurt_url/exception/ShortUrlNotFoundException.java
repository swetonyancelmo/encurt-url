package com.swetonyancelmo.encurt_url.exception;

public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String code) {
        super("URL curta não encontrada, inativa ou expirada: " + code);
    }
}
