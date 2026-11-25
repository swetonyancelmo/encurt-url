package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.model.ShortUrl;

/** Resultado da criação: o token em texto puro só existe aqui e na resposta da criação. */
public record CreatedShortUrl(ShortUrl shortUrl, String managementToken) {
}
