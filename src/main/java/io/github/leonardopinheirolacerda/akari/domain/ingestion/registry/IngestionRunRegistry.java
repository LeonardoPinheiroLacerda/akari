package io.github.leonardopinheirolacerda.akari.domain.ingestion.registry;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedup de execução concorrente da ingestão por {@code (providerId, rootPath)}. Só em memória —
 * não precisa sobreviver a restart (app de uso pessoal, instância única), e um disparo repetido
 * depois de reiniciar a aplicação simplesmente roda de novo, o que é seguro (reconciliação é
 * idempotente).
 */
@ApplicationScoped
public class IngestionRunRegistry {

    private final Set<String> runningKeys = ConcurrentHashMap.newKeySet();

    /**
     * Tenta registrar o início de uma execução. Atômico — chamadas concorrentes pra mesma chave
     * nunca registram as duas.
     *
     * @param providerId id do storage provider
     * @param rootPath subtree sendo ingerido; {@code null}/vazio significa o provider inteiro
     * @return {@code true} se essa chamada registrou (ninguém mais está rodando essa chave
     *         agora); {@code false} se já havia uma execução em andamento
     */
    public boolean tryStart(Integer providerId, String rootPath) {
        final String key = keyOf(providerId, rootPath);
        return runningKeys.add(key);
    }

    /**
     * Libera a chave ao final da execução (sucesso ou falha).
     *
     * @param providerId id do storage provider
     * @param rootPath o mesmo subtree passado em {@link #tryStart}
     */
    public void finish(Integer providerId, String rootPath) {
        final String key = keyOf(providerId, rootPath);
        runningKeys.remove(key);
    }

    private static String keyOf(Integer providerId, String rootPath) {
        final String normalizedRootPath = rootPath == null
                ? ""
                : rootPath;

        return providerId + "::" + normalizedRootPath;
    }

}
