package com.swetonyancelmo.encurt_url.exception;

public class InvalidManagementTokenException extends RuntimeException {

    public InvalidManagementTokenException() {
        super("Token de gerenciamento inválido para esta URL");
    }
}
