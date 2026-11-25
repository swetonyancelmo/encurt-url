package com.swetonyancelmo.encurt_url.exception;

public class MissingManagementTokenException extends RuntimeException {

    public MissingManagementTokenException() {
        super("Informe o token de gerenciamento no header 'Authorization: Bearer <token>'");
    }
}
