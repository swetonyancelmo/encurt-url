package com.swetonyancelmo.encurt_url.service;

import com.swetonyancelmo.encurt_url.model.ShortUrl;
import com.swetonyancelmo.encurt_url.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste uma URL curta em uma transação própria e curta.
 *
 * <p>Vive em um bean separado de propósito: se o INSERT violar a constraint UNIQUE, a transação
 * fica marcada como rollback-only e não pode ser reaproveitada. Quem chama (o laço de retry em
 * {@link ShortUrlService#create}) precisa estar FORA de qualquer transação, para que cada
 * tentativa abra uma transação nova através do proxy do Spring. Uma chamada interna dentro do
 * mesmo bean (this.insert) ignoraria o proxy e o {@code @Transactional} não teria efeito.
 */
@Component
@RequiredArgsConstructor
public class ShortUrlPersister {

    private final ShortUrlRepository shortUrlRepository;

    /**
     * {@code saveAndFlush} força o INSERT agora, dentro desta transação, para que a violação de
     * unicidade estoure aqui e não em um flush/commit tardio fora do controle do chamador.
     */
    @Transactional
    public ShortUrl insert(ShortUrl shortUrl) {
        return shortUrlRepository.saveAndFlush(shortUrl);
    }
}
