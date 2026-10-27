package io.github.leonardopinheirolacerda.akari.domain.playback.registry;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rastreia, em memória, os processos {@code ffmpeg} vivos de cada sessão de playback: se ainda
 * estão de pé, se o encerramento foi pedido pelo backend (seek, troca de faixa, expiração) e as
 * últimas linhas do stderr — pra diagnosticar uma morte inesperada sem depender do diretório de
 * trabalho, que é apagado na limpeza.
 *
 * <p>Estado local desta instância da aplicação, não persistido — uma sessão iniciada antes de um
 * reinício do processo akari deixa de ser rastreada, caindo de volta no comportamento de "ainda
 * não gerou a playlist" até expirar por heartbeat. Aceitável dado que a sessão já é 100% efêmera
 * (ver {@code PlaybackSession}).
 */
@ApplicationScoped
public class TranscodeProcessRegistry {

    private final Map<UUID, TrackedTranscodeProcess> tracked = new ConcurrentHashMap<>();

    /**
     * Começa a rastrear um processo {@code ffmpeg} recém-iniciado: drena o stderr dele num thread
     * dedicado (evita travar o processo quando o pipe enche) e loga o encerramento, esperado ou
     * não, quando ele sair.
     *
     * @param sessionId sessão dona do processo
     * @param process processo recém-iniciado pelo {@code FfmpegClient}
     */
    public void track(UUID sessionId, Process process) {
        final TrackedTranscodeProcess entry = new TrackedTranscodeProcess(process);
        tracked.put(sessionId, entry);

        startStderrReader(sessionId, entry);

        process.onExit().thenAccept(exited -> onExit(sessionId, entry, exited));
    }

    /**
     * Sinaliza que o próximo encerramento deste processo foi pedido pelo backend (seek, troca de
     * faixa ou expiração) — evita logar como falha inesperada um encerramento intencional.
     *
     * @param sessionId sessão cujo processo está sendo encerrado
     */
    public void markStoppedByBackend(UUID sessionId) {
        final TrackedTranscodeProcess entry = tracked.get(sessionId);

        if (entry != null) {
            entry.stoppedByBackend = true;
        }
    }

    /**
     * Para de rastrear o processo da sessão — chamado depois que a sessão é encerrada (reinício
     * ou expiração), pra não acumular entradas de sessões que já terminaram.
     *
     * @param sessionId sessão a esquecer
     */
    public void forget(UUID sessionId) {
        tracked.remove(sessionId);
    }

    /**
     * Diz se o processo rastreado desta sessão ainda está de pé.
     *
     * @param sessionId sessão a consultar
     * @return {@code true} se há um processo rastreado e ele ainda está vivo; {@code false} se já
     *         morreu, ou se nenhum processo desta sessão está sendo rastreado
     */
    public boolean isAlive(UUID sessionId) {
        final TrackedTranscodeProcess entry = tracked.get(sessionId);

        return entry != null && entry.process.isAlive();
    }

    /**
     * Diz se o processo rastreado desta sessão morreu sem ter sido pedido pelo backend — o sinal
     * de que o transcode falhou e nunca vai gerar a playlist.
     *
     * @param sessionId sessão a consultar
     * @return {@code true} se há um processo rastreado, ele já morreu e {@link #markStoppedByBackend}
     *         não foi chamado antes de morrer; {@code false} nos demais casos
     */
    public boolean hasFailedUnexpectedly(UUID sessionId) {
        final TrackedTranscodeProcess entry = tracked.get(sessionId);

        return entry != null && !entry.process.isAlive() && !entry.stoppedByBackend;
    }

    /**
     * Registra, uma única vez por sessão, que a playlist continua ausente com o processo já
     * morto — evita repetir o mesmo aviso a cada requisição de playlist da sessão.
     *
     * @param sessionId sessão cuja playlist nunca apareceu
     */
    public void logMissingPlaylistOnce(UUID sessionId) {
        final TrackedTranscodeProcess entry = tracked.get(sessionId);

        if (entry != null && !entry.missingPlaylistLogged) {
            entry.missingPlaylistLogged = true;
            Log.warnf("Sessão de playback %s: ffmpeg encerrado e a playlist nunca foi gerada", sessionId);
        }
    }

    private void startStderrReader(UUID sessionId, TrackedTranscodeProcess entry) {
        final Thread reader = new Thread(() -> drainStderr(sessionId, entry));
        reader.setName("ffmpeg-stderr-" + sessionId);
        reader.setDaemon(true);
        reader.start();
    }

    private void drainStderr(UUID sessionId, TrackedTranscodeProcess entry) {
        try (BufferedReader stderr = entry.process.errorReader()) {
            String line = stderr.readLine();

            while (line != null) {
                entry.appendStderrLine(line);
                line = stderr.readLine();
            }
        } catch (IOException e) {
            Log.warnf(e, "Falha lendo o stderr do ffmpeg da sessão %s", sessionId);
        }
    }

    private void onExit(UUID sessionId, TrackedTranscodeProcess entry, Process exited) {
        final long pid = exited.pid();
        final int exitCode = exited.exitValue();

        if (entry.stoppedByBackend) {
            Log.infof("ffmpeg da sessão %s (pid %d) encerrado a pedido do backend, código de saída %d", sessionId, pid, exitCode);
            return;
        }

        Log.errorf("ffmpeg da sessão %s (pid %d) terminou inesperadamente, código de saída %d", sessionId, pid, exitCode);

        final List<String> stderrTail = entry.stderrTailSnapshot();

        if (!stderrTail.isEmpty()) {
            Log.errorf(
                    "Últimas %d linha(s) do stderr do ffmpeg da sessão %s:%n%s",
                    stderrTail.size(),
                    sessionId,
                    String.join("\n", stderrTail));
        }
    }

}
