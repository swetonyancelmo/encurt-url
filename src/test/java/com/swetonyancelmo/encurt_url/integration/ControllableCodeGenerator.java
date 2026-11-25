package com.swetonyancelmo.encurt_url.integration;

import com.swetonyancelmo.encurt_url.service.ShortCodeGenerator;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerador de testes: permite forçar códigos específicos para provocar colisões de forma
 * determinística. Quando não há nada forçado, delega ao gerador real.
 */
public class ControllableCodeGenerator extends ShortCodeGenerator {

    private final Queue<String> forced = new ConcurrentLinkedQueue<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String collideOnFirstCallPerThread;
    private final ThreadLocal<Boolean> alreadyCollided = ThreadLocal.withInitial(() -> false);

    /** Os próximos códigos devolvidos, na ordem. */
    public void force(String... codes) {
        forced.addAll(List.of(codes));
    }

    /** Cada thread recebe {@code code} na primeira chamada e códigos aleatórios nas seguintes. */
    public void collideOnFirstCallPerThread(String code) {
        this.collideOnFirstCallPerThread = code;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        forced.clear();
        calls.set(0);
        collideOnFirstCallPerThread = null;
    }

    @Override
    public String next() {
        calls.incrementAndGet();
        String code = forced.poll();
        if (code != null) {
            return code;
        }
        if (collideOnFirstCallPerThread != null && !alreadyCollided.get()) {
            alreadyCollided.set(true);
            return collideOnFirstCallPerThread;
        }
        return super.next();
    }

    /** Limpa o estado por thread da thread atual. */
    public void clearThreadState() {
        alreadyCollided.remove();
    }
}
